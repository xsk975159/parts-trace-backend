package com.kewei.parts.infrastructure.chainmaker;

import com.alibaba.fastjson.JSON;
import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.storage.*;
import org.chainmaker.pb.common.ResultOuterClass;
import org.chainmaker.sdk.*;
import org.chainmaker.sdk.config.SdkConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.InputStream;
import java.util.*;

/** 仅处理长安链的SDK传输；跨平台路由与中继校验在ChainSdkRouter中执行。 */
@Component
@ConditionalOnExpression(ChainModes.REAL)
public class ChainMakerSdkInvoker implements ChainDriver {
    private final ResourceLoader resources;
    private final ChainNetworkProperties properties;
    private final Map<String, ChainClient> clients = new HashMap<>();

    public ChainMakerSdkInvoker(ResourceLoader resources, ChainNetworkProperties properties) {
        this.resources = resources;
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws Exception {
        try {
            for (Map.Entry<String, ChainNetworkProperties.Network> entry : properties.getNetworks().entrySet()) {
                ChainNetworkProperties.Network network = entry.getValue();
                if (network.getPlatform() != platform()) continue;
                try (InputStream stream = resources.getResource(network.getSdkConfig()).getInputStream()) {
                    SdkConfig configuration = new Yaml().loadAs(stream, SdkConfig.class);
                    if (configuration == null) throw new IllegalArgumentException("SDK配置为空");
                    if (configuration.getChainClient() == null) configuration.setChainClient(configuration.getChain_client());
                    ChainMakerKeyMaterial.prepare(configuration.getChainClient());
                    ChainClient client = ChainManager.getInstance().createChainClient(configuration);
                    clients.put(entry.getKey(), client);
                    if (client.getChainId() == null || client.getChainId().isEmpty()) throw new IllegalStateException("无法确认长安链物理链ID");
                    if (network.getExpectedChainId() != null && !network.getExpectedChainId().isEmpty()
                            && !network.getExpectedChainId().equals(client.getChainId())) throw new IllegalStateException("长安链物理链ID与配置不符");
                }
            }
        } catch (Exception error) { close(); throw new IllegalStateException("长安链SDK初始化失败", error); }
    }

    @Override public ChainPlatform platform() { return ChainPlatform.CHAINMAKER; }
    @Override public boolean configured(String code) { return clients.containsKey(code); }
    @Override public String physicalIdentity(String code) { return "CHAINMAKER:" + client(code).getChainId(); }
    public boolean isReady() { return !clients.isEmpty(); }

    private ChainClient client(String code) {
        ChainClient client = clients.get(code);
        if (client == null) throw new IllegalStateException("未配置长安链连接：" + code);
        return client;
    }

    @Override
    public String query(String code, String method, Map<String, byte[]> arguments) throws Exception {
        ResultOuterClass.TxResponse response = client(code).queryContract(properties.network(code).getContractName(), method, "", arguments, properties.getTimeoutMs());
        check(response);
        return response.getContractResult().getResult().toStringUtf8();
    }

    @Override
    public ChainStorageAdapter.StoreChainResult invoke(String code, String method, Map<String, byte[]> arguments) throws Exception {
        ResultOuterClass.TxResponse response = client(code).invokeContract(properties.network(code).getContractName(), method, "", arguments, properties.getTimeoutMs(), properties.getTimeoutMs());
        check(response);
        long height = confirmedHeight(client(code), response, properties.network(code).getContractName(), method, properties.getTimeoutMs());
        return new ChainStorageAdapter.StoreChainResult(response.getTxId(), height);
    }

    /** SDK 2.3.3 的同步响应可能未填高度，须从已入块交易补取，不能把0当作成功高度。 */
    public static long confirmedHeight(ChainClient client, ResultOuterClass.TxResponse response,
                                       String contract, String method, long timeout) throws Exception {
        if (response == null || response.getTxId().isEmpty()) throw new ChainClientException("未获得长安链交易编号");
        if (response.getTxBlockHeight() > 0) return response.getTxBlockHeight();
        long deadline = System.currentTimeMillis() + timeout;
        do {
            org.chainmaker.pb.common.ChainmakerTransaction.TransactionInfo info = client.getTxByTxId(response.getTxId(),
                    Math.max(1, Math.min(5000, deadline - System.currentTimeMillis())));
            if (info != null && info.hasTransaction() && info.getBlockHeight() > 0) {
                org.chainmaker.pb.common.ChainmakerTransaction.Transaction tx = info.getTransaction();
                if (!tx.hasPayload() || !tx.hasResult() || tx.getResult().getCode() != ResultOuterClass.TxStatusCode.SUCCESS
                        || !tx.getResult().hasContractResult() || tx.getResult().getContractResult().getCode() != 0
                        || !response.getTxId().equals(tx.getPayload().getTxId()) || !contract.equals(tx.getPayload().getContractName())
                        || !method.equals(tx.getPayload().getMethod()) || !client.getChainId().equals(tx.getPayload().getChainId()))
                    throw new ChainClientException("长安链入块交易结果或身份不匹配");
                return info.getBlockHeight();
            }
            Thread.sleep(Math.min(200, Math.max(1, deadline - System.currentTimeMillis())));
        } while (System.currentTimeMillis() < deadline);
        throw new ChainClientException("长安链交易尚未确认：" + response.getTxId());
    }

    public static void check(ResultOuterClass.TxResponse response) throws ChainClientException {
        if (response == null || response.getCode() != ResultOuterClass.TxStatusCode.SUCCESS || !response.hasContractResult() || response.getContractResult().getCode() != 0)
            throw new ChainClientException("长安链合约执行失败或回执不完整" + (response == null ? "" :
                    "：" + response.getCode() + "；" + response.getMessage()
                            + (response.hasContractResult() ? "；" + response.getContractResult().getMessage()
                                + "；" + response.getContractResult().getResult().toStringUtf8() : "")));
    }

    @Override public void verifyAnchorTransaction(String code,CrossChainAnchorPayload payload) throws Exception {
        org.chainmaker.pb.common.ChainmakerTransaction.TransactionInfo info=client(code).getTxByTxId(payload.getSourceTxHash(),properties.getTimeoutMs());
        if(info==null || !info.hasTransaction() || info.getBlockHeight()<=0 || !Long.valueOf(info.getBlockHeight()).equals(payload.getSourceBlockHeight()))
            throw new IllegalStateException("源链交易不存在或区块高度不匹配");
        org.chainmaker.pb.common.ChainmakerTransaction.Transaction tx=info.getTransaction();
        if(!tx.hasPayload() || !tx.hasResult() || tx.getResult().getCode()!=ResultOuterClass.TxStatusCode.SUCCESS
                || !tx.getResult().hasContractResult() || tx.getResult().getContractResult().getCode()!=0
                || !Objects.equals(client(code).getChainId(),tx.getPayload().getChainId()) || !payload.getSourceTxHash().equals(tx.getPayload().getTxId())
                || !properties.network(code).getContractName().equals(tx.getPayload().getContractName()) || !"save_anchor".equals(tx.getPayload().getMethod()))
            throw new IllegalStateException("源链交易执行结果、链或合约身份不匹配");
        Map<String,byte[]> actual=new HashMap<>();
        for(org.chainmaker.pb.common.Request.KeyValuePair pair:tx.getPayload().getParametersList())
            if(actual.put(pair.getKey(),pair.getValue().toByteArray())!=null)throw new IllegalStateException("源链交易参数重复");
        ChainAnchorRequest request=ChainAnchorRequest.builder().businessType(payload.getBusinessType()).businessId(payload.getBusinessId())
                .partsDid(payload.getPartsDid()).dataHash(payload.getDataHash()).objectCid(payload.getObjectCid()).build();
        for(Map.Entry<String,byte[]> expected:ContractArguments.anchor(request).entrySet())
            if(!Arrays.equals(expected.getValue(),actual.get(expected.getKey())))throw new IllegalStateException("源链交易内容与待核验存证不一致");
    }

    @PreDestroy
    public void close() {
        for (ChainClient client : clients.values()) client.stop();
        clients.clear();
    }
}
