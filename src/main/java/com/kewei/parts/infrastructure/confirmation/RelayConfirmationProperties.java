package com.kewei.parts.infrastructure.confirmation;

import com.kewei.parts.infrastructure.chain.ChainModes;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.net.URI;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** 联合核验配置。协调端只有公钥；各机构在自己的进程中保存一把私钥。 */
@Data
@Component
@ConfigurationProperties(prefix = "relay.confirmation")
public class RelayConfirmationProperties {
    private boolean enabled;
    private boolean validatorEnabled;
    private boolean validatorOnly;
    private String committeeVersion = "1";
    private int faultyNodes = 1;
    private int timeoutMs = 3000;
    private String validatorId;
    @lombok.ToString.Exclude private String validatorPrivateKey;
    private String coordinatorPublicKey;
    private Map<String, Member> members = new TreeMap<>();
    @Value("${parts.trace.storage.chain-mode:db}") private String storageMode = "db";

    @Data
    public static class Member {
        private String organization;
        private String publicKey;
        /** 完整地址，例如 https://quality.example/internal/relay/endorse。 */
        private String endpoint;
    }
    public int threshold() { return 2 * faultyNodes + 1; }
    public static RSAPublicKey publicKey(String value) throws Exception {
        RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(value)));
        if (key.getModulus().bitLength() < 2048 || key.getModulus().bitLength() > 4096
                || key.getModulus().bitLength() % 8 != 0 || !BigInteger.valueOf(65537).equals(key.getPublicExponent()))
            throw new IllegalArgumentException("验证公钥须为2048至4096位、指数65537的RSA公钥");
        return key;
    }
    @PostConstruct public void validate() throws Exception {
        if (!enabled && !validatorEnabled && !validatorOnly) return;
        if (!ChainModes.isReal(storageMode)) throw new IllegalArgumentException("多机构核验仅允许在real链模式启用");
        if (validatorOnly && (!validatorEnabled || enabled)) throw new IllegalArgumentException("validator-only须启用验证接口并关闭协调功能");
        if (committeeVersion == null || !committeeVersion.matches("[1-9][0-9]{0,8}") || faultyNodes < 1 || faultyNodes > 3
                || members == null || members.size() != 3 * faultyNodes + 1 || timeoutMs < 500 || timeoutMs > 10000)
            throw new IllegalArgumentException("核验委员会须为4、7或10节点；版本和超时配置须有效");
        Set<String> organizations = new HashSet<>(), keys = new HashSet<>(), endpoints = new HashSet<>();
        for (Map.Entry<String, Member> entry : members.entrySet()) {
            Member member = entry.getValue();
            if (!entry.getKey().matches("[a-zA-Z0-9_-]{1,64}") || member == null || member.organization == null
                    || !member.organization.matches("[a-zA-Z0-9_-]{1,64}") || !organizations.add(member.organization)
                    || !keys.add(Base64.getEncoder().encodeToString(publicKey(member.publicKey).getEncoded())))
                throw new IllegalArgumentException("节点标识、机构标识或公钥非法/重复");
            if (enabled) {
                URI uri = new URI(member.endpoint);
                boolean local = Arrays.asList("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost());
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                        || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))
                        || !"/internal/relay/endorse".equals(uri.getPath()) || !endpoints.add(uri.normalize().toString()))
                    throw new IllegalArgumentException("验证地址须为独立HTTPS端点，本机测试可用HTTP；路径为/internal/relay/endorse");
            }
        }
        publicKey(coordinatorPublicKey);
        if (validatorEnabled) {
            Member self = members.get(validatorId);
            if (self == null || validatorPrivateKey == null
                    || !com.kewei.parts.utils.RsaUtil.verify("validator-key-check", com.kewei.parts.utils.RsaUtil.sign("validator-key-check", validatorPrivateKey), self.publicKey))
                throw new IllegalArgumentException("本验证节点的私钥与委员会公钥不匹配");
        }
    }
}
