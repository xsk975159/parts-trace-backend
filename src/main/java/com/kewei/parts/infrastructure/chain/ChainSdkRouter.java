package com.kewei.parts.infrastructure.chain;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.kewei.parts.infrastructure.chainmaker.CrossChainAnchorPayload;
import com.kewei.parts.infrastructure.chainmaker.RelayProof;
import com.kewei.parts.infrastructure.storage.*;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class ChainSdkRouter {
    private final ChainNetworkProperties properties;
    private final Map<ChainPlatform, ChainDriver> drivers = new EnumMap<>(ChainPlatform.class);

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
        JSONObject source = readAnchor(payload.getSourceChainId(), payload.getBusinessType(), payload.getBusinessId());
        if (source == null || !Objects.equals(source.getString("data_hash"), payload.getDataHash())
                || !Objects.equals(source.getString("parts_did"), payload.getPartsDid())
                || !Objects.equals(empty(source.getString("object_cid")), empty(payload.getObjectCid())))
            throw new IllegalStateException("源链存证摘要、主体或CID不一致，拒绝中继");
        Map<String, byte[]> signed = RelayProof.arguments(payload, System.currentTimeMillis() / 1000, properties.getRelayPrivateKey());
        return driver(ChainRouter.MAIN_REGULATOR).invoke(ChainRouter.MAIN_REGULATOR, "receive_cross_anchor", signed);
    }

    private static String empty(String value) { return value == null ? "" : value; }
}
