package com.kewei.parts.infrastructure.fisco;

import com.alibaba.fastjson.JSON;
import com.kewei.parts.infrastructure.chain.ChainNetworkProperties;
import lombok.Getter;
import lombok.Setter;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** 独立部署入口；不启动业务服务。默认只检查，显式--deploy才发送部署交易。 */
public final class FiscoContractDeployer {
    private FiscoContractDeployer() {}

    @Getter @Setter
    public static class Manifest {
        private String sdkConfig;
        private String group = "group0";
        private String expectedChainId;
        private String expectedAccount;
        private String localChainCode;
        private List<String> writerAddresses = new ArrayList<>();
        // 源链编码 -> X.509 DER公钥的Base64，不接受私钥。
        private Map<String, String> relayPublicKeys = new LinkedHashMap<>();
        private String binary = "classpath:fisco/TraceAnchor.bin";
        private String smBinary;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !"--deploy".equals(args[1])))
            throw new IllegalArgumentException("用法：FiscoContractDeployer manifest.json [--deploy]；默认只检查连接与部署参数");
        Manifest manifest = JSON.parseObject(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8), Manifest.class);
        List<Object> constructor = constructorArguments(manifest);
        ChainNetworkProperties.Network network = new ChainNetworkProperties.Network();
        network.setSdkConfig(manifest.sdkConfig);
        network.setGroup(manifest.group);
        network.setExpectedChainId(manifest.expectedChainId);
        network.setExpectedAccount(manifest.expectedAccount);
        ResourceLoader resources = new DefaultResourceLoader();
        try (FiscoConnection connection = new FiscoConnectionFactory(resources).connect(network)) {
            String binaryResource = connection.cryptoType() == 1 ? manifest.smBinary : manifest.binary;
            if (blank(binaryResource)) throw new IllegalArgumentException("国密群组须提供同版本国密编译器生成的smBinary，不能使用普通字节码");
            String binary;
            try (InputStream stream = resources.getResource(binaryResource).getInputStream()) {
                binary = StreamUtils.copyToString(stream, StandardCharsets.UTF_8).trim();
            }
            if (!binary.matches("[0-9a-fA-F]+") || binary.length() % 2 != 0)
                throw new IllegalArgumentException("合约字节码必须是无0x前缀的完整十六进制文本");
            System.out.println("部署参数已检查：" + connection.physicalIdentity() + "，逻辑链=" + manifest.localChainCode
                    + "，固定账户=" + connection.account() + "，cryptoType=" + connection.cryptoType());
            if (args.length == 1) {
                System.out.println("检查完成；未发送部署交易。增加--deploy参数后才部署。");
                return;
            }
            TransactionResponse response = connection.deploy(binary, constructor);
            validateDeployment(response, connection.account());
            String address = response.getContractAddress();
            CallResponse info = connection.queryAt(address, "contract_info", Collections.emptyList());
            if (info == null || info.getReturnCode() != 0 || info.getReturnObject() == null
                    || !info.getReturnObject().equals(Arrays.asList("parts-trace-anchor/v2", manifest.localChainCode)))
                throw new IllegalStateException("已发送部署交易，但新合约版本或逻辑链检查未通过；请核查回执，勿直接重试部署");
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("chainCode", manifest.localChainCode);
            receipt.put("contractAddress", address);
            receipt.put("transactionHash", response.getTransactionReceipt().getTransactionHash());
            receipt.put("blockNumber", response.getTransactionReceipt().getBlockNumber());
            System.out.println(JSON.toJSONString(receipt));
        }
    }

    public static List<Object> constructorArguments(Manifest manifest) throws Exception {
        if (manifest == null || blank(manifest.sdkConfig) || blank(manifest.group) || blank(manifest.expectedChainId))
            throw new IllegalArgumentException("部署清单须指定SDK配置、群组和预期物理链ID");
        if (manifest.expectedAccount == null || !manifest.expectedAccount.matches("0x[0-9a-fA-F]{40}")
                || manifest.expectedAccount.matches("0x0{40}")) throw new IllegalArgumentException("部署须指定固定的非零交易账户");
        if (manifest.localChainCode == null || !manifest.localChainCode.matches("[a-zA-Z0-9_-]{1,64}"))
            throw new IllegalArgumentException("部署清单的逻辑链编码不合法");
        if (manifest.writerAddresses == null || manifest.relayPublicKeys == null)
            throw new IllegalArgumentException("写入账户和中继公钥列表不能为null");
        Set<String> writers = new HashSet<>();
        for (String writer : manifest.writerAddresses) {
            if (writer == null || !writer.matches("0x[0-9a-fA-F]{40}") || writer.matches("0x0{40}")
                    || !writers.add(writer.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("写入账户非法或重复");
        }
        List<String> sources = new ArrayList<>();
        List<byte[]> moduli = new ArrayList<>(), exponents = new ArrayList<>();
        for (Map.Entry<String, String> entry : manifest.relayPublicKeys.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-zA-Z0-9_-]{1,64}"))
                throw new IllegalArgumentException("受信源链编码不合法");
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(entry.getValue())));
            int bits = key.getModulus().bitLength();
            if (bits < 2048 || bits > 4096 || bits % 8 != 0 || !key.getPublicExponent().equals(BigInteger.valueOf(65537)))
                throw new IllegalArgumentException("中继公钥须为2048至4096位、指数65537的RSA公钥，位数须为8的倍数");
            sources.add(entry.getKey());
            moduli.add(unsigned(key.getModulus()));
            exponents.add(unsigned(key.getPublicExponent()));
        }
        return Arrays.asList(manifest.localChainCode, manifest.writerAddresses, sources, moduli, exponents);
    }

    public static void validateDeployment(TransactionResponse response, String account) {
        TransactionReceipt receipt = response == null ? null : response.getTransactionReceipt();
        if (response == null || response.getReturnCode() != 0 || receipt == null || !receipt.isStatusOK()
                || receipt.getBlockNumber() == null || receipt.getBlockNumber().signum() <= 0
                || receipt.getTransactionHash() == null || !receipt.getTransactionHash().matches("0x[0-9a-fA-F]{64}")
                || receipt.getFrom() == null || !account.equalsIgnoreCase(receipt.getFrom())
                || response.getContractAddress() == null || !response.getContractAddress().matches("0x[0-9a-fA-F]{40}")
                || response.getContractAddress().matches("0x0{40}"))
            throw new IllegalStateException("未取得有效部署回执；请核查链上交易状态后再操作，避免重复部署");
    }

    private static byte[] unsigned(BigInteger number) {
        byte[] bytes = number.toByteArray();
        return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
