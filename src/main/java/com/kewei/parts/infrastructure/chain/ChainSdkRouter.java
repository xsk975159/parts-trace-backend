package com.kewei.parts.infrastructure.chain;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.kewei.parts.infrastructure.chainmaker.CrossChainAnchorPayload;
import com.kewei.parts.infrastructure.chainmaker.RelayProof;
import com.kewei.parts.infrastructure.storage.*;
import com.kewei.parts.infrastructure.confirmation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class ChainSdkRouter {
    private final ChainNetworkProperties properties;
    private final Map<ChainPlatform, ChainDriver> drivers = new EnumMap<>(ChainPlatform.class);
    @Autowired(required=false) private RelayConfirmationProperties confirmation;

    public ChainSdkRouter(ChainNetworkProperties properties, List<ChainDriver> drivers) {
        this.properties = properties;
        for (ChainDriver driver : drivers) {
            if (this.drivers.put(driver.platform(), driver) != null) throw new IllegalArgumentException("同一平台注册了多个SDK驱动");
        }
    }

    @PostConstruct
    public void verifyBindings() throws Exception {
        if (!properties.isRealMode()) return;
        Set<String> identities = new HashSet<>();
        for (String code : properties.getNetworks().keySet()) {
            ChainDriver driver = driver(code);
            if (!identities.add(driver.physicalIdentity(code))) throw new IllegalStateException("多个逻辑链指向同一平台账本：" + code);
            JSONObject info = JSON.parseObject(driver.query(code, "contract_info", Collections.emptyMap()));
            if (info == null || !"parts-trace-anchor/v2".equals(info.getString("schema")) || !code.equals(info.getString("chainCode")))
                throw new IllegalStateException("合约版本或逻辑链编码不匹配：" + code);
        }
    }

    private ChainDriver driver(String code) {
        ChainNetworkProperties.Network network = properties.network(code);
        ChainDriver driver = drivers.get(network.getPlatform());
        if (driver == null || !driver.configured(code)) throw new IllegalStateException("平台SDK连接未就绪：" + code + " / " + network.getPlatform());
        return driver;
    }

    public ChainPlatform platform(String code) { return properties.network(code).getPlatform(); }

    public ChainStorageAdapter.StoreChainResult storeAnchor(ChainAnchorRequest request) throws Exception {
        return driver(request.getChainId()).invoke(request.getChainId(), "save_anchor", ContractArguments.anchor(request));
    }

    public String getAnchorHash(String code, String type, Long id) throws Exception {
        JSONObject anchor = readAnchor(code, type, id);
        return anchor == null ? null : anchor.getString("data_hash");
    }

    private JSONObject readAnchor(String code, String type, Long id) throws Exception {
        String raw = driver(code).query(code, "get_anchor", ContractArguments.anchorKey(type, id));
        return raw == null || raw.isEmpty() ? null : JSON.parseObject(raw);
    }

    public void appendVc(String did, String id, String hash) throws Exception {
        driver(ChainRouter.MAIN_REGULATOR).invoke(ChainRouter.MAIN_REGULATOR, "append_vc", ContractArguments.vc(did, id, hash));
    }

    public void revokeVc(String did, String id) throws Exception {
        driver(ChainRouter.MAIN_REGULATOR).invoke(ChainRouter.MAIN_REGULATOR, "revoke_vc", ContractArguments.vc(did, id, null));
    }

    public String getVcHash(String did, String id) throws Exception {
        return driver(ChainRouter.MAIN_REGULATOR).query(ChainRouter.MAIN_REGULATOR, "get_vc", ContractArguments.vc(did, id, null));
    }

    public ChainStorageAdapter.StoreChainResult relay(CrossChainAnchorPayload payload) throws Exception {
        if(confirmation!=null && confirmation.isEnabled())throw new IllegalStateException("已启用多机构确认，不能调用单中继提交入口");
        verifySource(payload);
        Map<String, byte[]> signed = RelayProof.arguments(payload, System.currentTimeMillis() / 1000, properties.getRelayPrivateKey());
        return driver(ChainRouter.MAIN_REGULATOR).invoke(ChainRouter.MAIN_REGULATOR, "receive_cross_anchor", signed);
    }

    public void verifySource(CrossChainAnchorPayload payload) throws Exception {
        JSONObject source = readAnchor(payload.getSourceChainId(), payload.getBusinessType(), payload.getBusinessId());
        if (source == null || !Objects.equals(source.getString("data_hash"), payload.getDataHash())
                || !Objects.equals(source.getString("parts_did"), payload.getPartsDid())
                || !Objects.equals(empty(source.getString("object_cid")), empty(payload.getObjectCid())))
            throw new IllegalStateException("源链存证摘要、主体或CID不一致，拒绝中继");
    }

    public ChainStorageAdapter.StoreChainResult relayConfirmed(RelayCertificate certificate) throws Exception {
        if(confirmation==null || !confirmation.isEnabled())throw new IllegalStateException("多机构确认未启用");
        ConfirmationProof.verifyCertificate(certificate,confirmation,System.currentTimeMillis()/1000);
        verifyRelayPolicy(confirmation);verifyConfirmedSource(certificate.getPayload());
        return driver(ChainRouter.MAIN_REGULATOR).invoke(ChainRouter.MAIN_REGULATOR,"receive_cross_anchor_quorum",ConfirmationProof.arguments(certificate));
    }

    public void verifyConfirmedSource(CrossChainAnchorPayload payload) throws Exception {
        verifySource(payload);
        driver(payload.getSourceChainId()).verifyAnchorTransaction(payload.getSourceChainId(),payload);
    }

    /** 必须与目标合约的名单和门槛完全一致，不能在旧合约上只做应用层投票。 */
    public void verifyRelayPolicy(RelayConfirmationProperties policy) throws Exception {
        JSONObject actual=JSON.parseObject(driver(ChainRouter.MAIN_REGULATOR).query(ChainRouter.MAIN_REGULATOR,"relay_policy",Collections.emptyMap()));
        JSONObject members=actual==null?null:actual.getJSONObject("members");
        if(actual==null || !policy.getCommitteeVersion().equals(actual.getString("version"))
                || actual.getIntValue("threshold")!=policy.threshold() || members==null || members.size()!=policy.getMembers().size())
            throw new IllegalStateException("主链核验委员会版本、门槛或名单不匹配");
        for(Map.Entry<String,RelayConfirmationProperties.Member> entry:policy.getMembers().entrySet()) {
            JSONObject member=members.getJSONObject(entry.getKey());
            if(member==null || !entry.getValue().getOrganization().equals(member.getString("organization"))
                    || !Arrays.equals(RelayConfirmationProperties.publicKey(entry.getValue().getPublicKey()).getEncoded(),
                    RelayConfirmationProperties.publicKey(member.getString("publicKey")).getEncoded()))
                throw new IllegalStateException("主链核验节点机构或公钥不匹配："+entry.getKey());
        }
    }

    private static String empty(String value) { return value == null ? "" : value; }
}
