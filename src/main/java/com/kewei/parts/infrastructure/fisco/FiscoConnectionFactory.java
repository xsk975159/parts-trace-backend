package com.kewei.parts.infrastructure.fisco;

import com.kewei.parts.infrastructure.chain.ChainNetworkProperties;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.transaction.manager.*;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class FiscoConnectionFactory {
    private final ResourceLoader resources;
    public FiscoConnectionFactory(ResourceLoader resources) { this.resources = resources; }

    public FiscoConnection connect(ChainNetworkProperties.Network configuration) throws Exception {
        BcosSDK sdk = null;
        try {
            // SDK按磁盘配置读取证书和账户文件，避免把部署私钥打进JAR。
            String file = resources.getResource(configuration.getSdkConfig()).getFile().getCanonicalPath();
            sdk = BcosSDK.build(file);
            Client client = sdk.getClient(configuration.getGroup());
            if (Boolean.TRUE.equals(client.isWASM())) throw new IllegalArgumentException("TraceAnchor需要FISCO EVM群组，不能使用WASM群组");
            if (client.getChainId() == null || client.getChainId().trim().isEmpty() || !configuration.getGroup().equals(client.getGroup()))
                throw new IllegalStateException("FISCO链或群组身份不可确认");
            if (configuration.getExpectedChainId() != null && !configuration.getExpectedChainId().isEmpty()
                    && !configuration.getExpectedChainId().equals(client.getChainId()))
                throw new IllegalStateException("FISCO物理链ID与配置不符");
            String account = client.getCryptoSuite().getCryptoKeyPair().getAddress();
            if (!configuration.getExpectedAccount().equalsIgnoreCase(account))
                throw new IllegalStateException("FISCO SDK未加载指定的固定账户，请核对accountFilePath与expected-account");
            String abi;
            try (InputStream stream = resources.getResource(configuration.getAbi()).getInputStream()) {
                abi = StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
            }
            AssembleTransactionProcessor transactions = TransactionProcessorFactory.createAssembleTransactionProcessor(client, client.getCryptoSuite().getCryptoKeyPair());
            return new FiscoConnection(sdk, client, transactions, abi, configuration.getContractAddress());
        } catch (Exception | LinkageError error) {
            if (sdk != null) sdk.stopAll();
            throw new IllegalStateException("FISCO BCOS 3.x SDK初始化失败，请检查证书、固定账户、群组和本机JNI运行库", error);
        }
    }
}
