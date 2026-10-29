package com.kewei.parts.infrastructure.fisco;

import com.alibaba.fastjson.JSON;
import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.model.dto.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;

@Component
@ConditionalOnExpression(ChainModes.REAL)
public class FiscoBcosDriver implements ChainDriver {
    public static final List<String> CROSS_FIELDS = Collections.unmodifiableList(Arrays.asList("source_chain_id", "source_tx_hash", "source_block_height",
            "business_type", "business_id", "parts_did", "data_hash", "object_cid", "target_chain_id", "issued_at"));
    private final ChainNetworkProperties properties;
    private final FiscoConnectionFactory factory;
    private final Map<String, FiscoConnection> connections = new HashMap<>();

    public FiscoBcosDriver(ChainNetworkProperties properties, FiscoConnectionFactory factory) {
        this.properties = properties;
        this.factory = factory;
    }

    @PostConstruct
    public void initialize() throws Exception {
        try {
            for (Map.Entry<String, ChainNetworkProperties.Network> entry : properties.getNetworks().entrySet()) {
                if (entry.getValue().getPlatform() == platform()) connections.put(entry.getKey(), factory.connect(entry.getValue()));
            }
        } catch (Exception error) { close(); throw error; }
    }

    @Override public ChainPlatform platform() { return ChainPlatform.FISCO_BCOS; }
    @Override public boolean configured(String code) { return connections.containsKey(code); }
    @Override public String physicalIdentity(String code) { return connection(code).physicalIdentity(); }

    private FiscoConnection connection(String code) {
        FiscoConnection connection = connections.get(code);
        if (connection == null) throw new IllegalStateException("未配置FISCO连接：" + code);
        return connection;
    }

    @Override
    public ChainStorageAdapter.StoreChainResult invoke(String code, String method, Map<String, byte[]> arguments) throws Exception {
        FiscoConnection connection = connection(code);
        TransactionResponse response = connection.invoke(method, parameters(method, arguments));
        if (response == null || response.getReturnCode() != 0) throw new IllegalStateException("FISCO合约调用失败：" + method);
        TransactionReceipt receipt = response.getTransactionReceipt();
        if (receipt == null || !receipt.isStatusOK() || receipt.getStatus() != 0 || receipt.getBlockNumber() == null || receipt.getBlockNumber().signum() <= 0
                || receipt.getTransactionHash() == null || !receipt.getTransactionHash().matches("0x[0-9a-fA-F]{64}")
                || !connection.address().equalsIgnoreCase(receipt.getTo()) || !connection.account().equalsIgnoreCase(receipt.getFrom()))
            throw new IllegalStateException("FISCO回执不完整、执行失败或交易身份不匹配");
        return new ChainStorageAdapter.StoreChainResult(receipt.getTransactionHash(), receipt.getBlockNumber().longValueExact());
    }

    @Override
    public String query(String code, String method, Map<String, byte[]> arguments) throws Exception {
        CallResponse response = connection(code).query(method, parameters(method, arguments));
        if (response == null || response.getReturnCode() != 0 || response.getReturnObject() == null)
            throw new IllegalStateException("FISCO合约查询失败：" + method);
        List<Object> values = response.getReturnObject();
        if("relay_policy".equals(method)) {
            requireSize(values,6);
            List<?> ids=(List<?>)values.get(2),organizations=(List<?>)values.get(3),moduli=(List<?>)values.get(4),exponents=(List<?>)values.get(5);
            if(ids.size()!=organizations.size() || ids.size()!=moduli.size() || ids.size()!=exponents.size())throw new IllegalStateException("FISCO委员会返回参数不符");
            Map<String,Object> members=new LinkedHashMap<>();
            for(int i=0;i<ids.size();i++) {
                String publicKey=Base64.getEncoder().encodeToString(KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(new BigInteger(1,(byte[])moduli.get(i)),new BigInteger(1,(byte[])exponents.get(i)))).getEncoded());
                Map<String,String> member=new LinkedHashMap<>();member.put("organization",string(organizations.get(i)));member.put("publicKey",publicKey);
                if(members.put(string(ids.get(i)),member)!=null)throw new IllegalStateException("FISCO委员会节点重复");
            }
            Map<String,Object> policy=new LinkedHashMap<>();policy.put("version",string(values.get(0)));policy.put("threshold",values.get(1));policy.put("members",members);
            return JSON.toJSONString(policy);
        }
        if ("contract_info".equals(method)) {
            requireSize(values, 2);
            Map<String, String> info = new LinkedHashMap<>();
            info.put("schema", string(values.get(0)));
            info.put("chainCode", string(values.get(1)));
            return JSON.toJSONString(info);
        }
        if ("get_vc".equals(method)) { requireSize(values, 1); return string(values.get(0)); }
        if ("get_anchor".equals(method) || "get_cross_anchor".equals(method)) {
            requireSize(values, 3);
            if (string(values.get(0)).isEmpty()) return "";
            Map<String, String> anchor = new LinkedHashMap<>();
            anchor.put("data_hash", string(values.get(0)));
            anchor.put("object_cid", string(values.get(1)));
            anchor.put("parts_did", string(values.get(2)));
            return JSON.toJSONString(anchor);
        }
        throw new IllegalArgumentException("不支持的FISCO查询：" + method);
    }

    /** 与编译产出的ABI保持一致，不允许把长安链map直接当作EVM调用参数。 */
    public static List<Object> parameters(String method, Map<String, byte[]> arguments) {
        switch (method) {
            case "save_anchor": return strings(arguments, "business_type", "business_id", "data_hash", "object_cid", "parts_did");
            case "get_anchor": return strings(arguments, "business_type", "business_id");
            case "append_vc": return strings(arguments, "parts_did", "vc_id", "vc_hash");
            case "get_vc": case "revoke_vc": return strings(arguments, "parts_did", "vc_id");
            case "get_cross_anchor": return strings(arguments, "source_chain_id", "source_tx_hash");
            case "contract_info": case "relay_policy": return Collections.emptyList();
            case "configure_relay_committee": {
                try {
                    com.alibaba.fastjson.JSONObject policy=JSON.parseObject(ContractArguments.text(arguments,"committee"));
                    String version=policy.getString("version");
                    if(version==null || !version.matches("[1-9][0-9]{0,8}"))throw new IllegalArgumentException("委员会版本不合法");
                    FiscoContractDeployer.Manifest manifest=new FiscoContractDeployer.Manifest();manifest.setCommitteeVersion("1");
                    com.alibaba.fastjson.JSONObject members=policy.getJSONObject("members");
                    if(members==null)throw new IllegalArgumentException("缺少委员会成员");
                    for(String id:members.keySet())manifest.getCommitteeMembers().put(id,members.getObject(id,com.kewei.parts.infrastructure.confirmation.RelayConfirmationProperties.Member.class));
                    List<Object> configured=FiscoContractDeployer.committeeArguments(manifest);
                    if(configured==null || policy.getIntValue("threshold")!=2*((members.size()-1)/3)+1)throw new IllegalArgumentException("委员会门槛不合法");
                    configured.set(0,version);return configured;
                } catch(Exception error) {throw new IllegalArgumentException("委员会配置不合法",error);}
            }
            case "receive_cross_anchor_quorum":
                List<String> quorumFields=new ArrayList<>();for(String key:CROSS_FIELDS)quorumFields.add(ContractArguments.text(arguments,key));
                List<String> ids=JSON.parseArray(ContractArguments.text(arguments,"validator_ids"),String.class);
                List<String> signatures=JSON.parseArray(ContractArguments.text(arguments,"validator_signatures"),String.class);
                if(ids==null || signatures==null || ids.size()!=signatures.size() || ids.isEmpty() || ids.size()>10)throw new IllegalArgumentException("核验签名列表不合法");
                List<byte[]> decoded=new ArrayList<>();for(String signed:signatures)decoded.add(Base64.getDecoder().decode(signed));
                return Arrays.asList(quorumFields,ContractArguments.text(arguments,"committee_version"),ids,decoded);
            case "receive_cross_anchor":
                List<String> fields = new ArrayList<>();
                for (String key : CROSS_FIELDS) fields.add(ContractArguments.text(arguments, key));
                String signature = ContractArguments.text(arguments, "signature");
                if (signature.isEmpty()) throw new IllegalArgumentException("跨链消息缺少签名");
                return Arrays.asList(fields, Base64.getDecoder().decode(signature));
            default: throw new IllegalArgumentException("不支持的FISCO合约方法：" + method);
        }
    }

    private static List<Object> strings(Map<String, byte[]> arguments, String... names) {
        List<Object> result = new ArrayList<>();
        for (String name : names) result.add(ContractArguments.text(arguments, name));
        return result;
    }
    private static String string(Object value) {
        if (!(value instanceof String)) throw new IllegalStateException("FISCO ABI返回类型不符");
        return (String) value;
    }
    @Override public void verifyAnchorTransaction(String code,com.kewei.parts.infrastructure.chainmaker.CrossChainAnchorPayload payload) throws Exception {
        FiscoConnection connection=connection(code);
        if(payload.getSourceTxHash()==null || !payload.getSourceTxHash().matches("0x[0-9a-f]{64}"))throw new IllegalArgumentException("FISCO源交易哈希须为标准小写十六进制");
        TransactionReceipt receipt=connection.receipt(payload.getSourceTxHash());
        if(receipt==null || receipt.getStatus()!=0 || !receipt.isStatusOK() || receipt.getBlockNumber()==null || receipt.getBlockNumber().signum()<=0
                || !receipt.getBlockNumber().equals(BigInteger.valueOf(payload.getSourceBlockHeight()))
                || !payload.getSourceTxHash().equalsIgnoreCase(receipt.getTransactionHash()) || !connection.address().equalsIgnoreCase(receipt.getTo()))
            throw new IllegalStateException("FISCO源交易未确认或交易、合约、高度不匹配");
        List<Object> fields=Arrays.asList(payload.getBusinessType(),String.valueOf(payload.getBusinessId()),payload.getDataHash(),payload.getObjectCid()==null?"":payload.getObjectCid(),payload.getPartsDid());
        String expected=connection.encodedInput("save_anchor",fields);
        if(expected==null || !expected.equalsIgnoreCase(receipt.getInput()))throw new IllegalStateException("FISCO源交易调用内容与待核验存证不一致");
    }
    private static void requireSize(List<?> values, int count) {
        if (values.size() != count) throw new IllegalStateException("FISCO ABI返回参数个数不符");
    }

    @PreDestroy public void close() {
        for (FiscoConnection connection : connections.values()) connection.close();
        connections.clear();
    }
}
