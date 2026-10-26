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
        if (response.getTxId().isEmpty() || response.getTxBlockHeight() <= 0) throw new ChainClientException("未获得长安链确认回执");
        return new ChainStorageAdapter.StoreChainResult(response.getTxId(), response.getTxBlockHeight());
    }

    public static void check(ResultOuterClass.TxResponse response) throws ChainClientException {
        if (response == null || response.getCode() != ResultOuterClass.TxStatusCode.SUCCESS || !response.hasContractResult() || response.getContractResult().getCode() != 0)
            throw new ChainClientException("长安链合约执行失败或回执不完整");
    }

    @PreDestroy
    public void close() {
        for (ChainClient client : clients.values()) client.stop();
        clients.clear();
    }
}
