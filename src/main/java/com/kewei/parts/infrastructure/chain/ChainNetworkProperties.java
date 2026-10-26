package com.kewei.parts.infrastructure.chain;

import com.kewei.parts.infrastructure.storage.ChainRouter;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "blockchain")
public class ChainNetworkProperties {
    private Map<String, Network> networks = defaults();
    private String relayPrivateKey;
    private long timeoutMs = 60000;

    @Value("${parts.trace.storage.chain-mode:db}")
    private String storageMode = "db";

    @Autowired private Environment environment;

    @Getter
    @Setter
    public static class Network {
        private ChainPlatform platform;
        private String sdkConfig;
        private String expectedChainId;
        private String contractName = "trace_anchor";
        private String group = "group0";
        private String contractAddress;
        private String expectedAccount;
        private String abi = "classpath:fisco/TraceAnchor.abi.json";
        private String binary = "classpath:fisco/TraceAnchor.bin";
        private String smBinary;
    }

    private static Map<String, Network> defaults() {
        Map<String, Network> result = new LinkedHashMap<>();
        for (String code : new String[]{ChainRouter.MAIN_REGULATOR, ChainRouter.SUB_PRODUCTION,
                ChainRouter.SUB_QUALITY, ChainRouter.SUB_LOGISTICS}) {
            Network network = new Network();
            network.setPlatform(ChainRouter.SUB_LOGISTICS.equals(code) ? ChainPlatform.FISCO_BCOS : ChainPlatform.CHAINMAKER);
            result.put(code, network);
        }
        return result;
    }

    public boolean isRealMode() {
        return ChainModes.isReal(storageMode);
    }

    public Network network(String code) {
        Network network = networks.get(code);
        if (network == null) throw new IllegalArgumentException("未配置逻辑链：" + code);
        return network;
    }

    @PostConstruct
    public void validate() {
        if (!"db".equals(storageMode) && !isRealMode()) throw new IllegalArgumentException("chain-mode只支持db或real，兼容旧值chainmaker");
        if (timeoutMs < 1000 || timeoutMs > 300000) throw new IllegalArgumentException("链调用超时须为1000至300000毫秒");
        if (networks == null || networks.isEmpty()) throw new IllegalArgumentException("至少配置一条逻辑链");
        for (Map.Entry<String, Network> entry : networks.entrySet()) {
            String code = entry.getKey();
            Network network = entry.getValue();
            if (!code.matches("[a-zA-Z0-9_-]{1,64}") || network == null || network.getPlatform() == null)
                throw new IllegalArgumentException("逻辑链编码或平台配置不合法：" + code);
            if (network.getPlatform() == ChainPlatform.CHAINMAKER && blank(network.getSdkConfig()) && environment != null) {
                network.setSdkConfig(environment.getProperty("chainmaker.connections." + code));
            }
            if (!isRealMode()) continue;
            if (blank(network.getSdkConfig())) throw new IllegalArgumentException("逻辑链缺少SDK配置：" + code);
            if (network.getPlatform() == ChainPlatform.CHAINMAKER && blank(network.getContractName()))
                throw new IllegalArgumentException("长安链缺少合约名：" + code);
            if (network.getPlatform() == ChainPlatform.FISCO_BCOS) {
                if (blank(network.getGroup())) throw new IllegalArgumentException("FISCO BCOS缺少群组名：" + code);
                if (network.getContractAddress() == null || !network.getContractAddress().matches("0x[0-9a-fA-F]{40}")
                        || network.getContractAddress().matches("0x0{40}"))
                    throw new IllegalArgumentException("FISCO BCOS缺少有效的已部署合约地址：" + code);
                if (network.getExpectedAccount() == null || !network.getExpectedAccount().matches("0x[0-9a-fA-F]{40}"))
                    throw new IllegalArgumentException("FISCO BCOS须配置固定交易账户expected-account：" + code);
            }
        }
        if (isRealMode() && blank(relayPrivateKey)) throw new IllegalArgumentException("真实模式缺少RELAY_PRIVATE_KEY");
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
