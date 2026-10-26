package com.kewei.parts.security;

import com.kewei.parts.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** 服务端托管私钥，主密钥通过环境变量提供，禁止存入业务数据库。 */
@Component
public class KeyVault {
    private static final String PREFIX = "enc:v1:";
    @Value("${security.key-encryption-key:}") private String masterKey;

    public boolean isEncrypted(String value) { return value != null && value.startsWith(PREFIX); }

    private SecretKeySpec key() {
        try {
            byte[] decoded = Base64.getDecoder().decode(masterKey);
            if (decoded.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(decoded, "AES");
        } catch (Exception e) {
            throw new BusinessException("请配置32字节Base64格式的 KEY_ENCRYPTION_KEY 后使用托管签名密钥");
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) throw new BusinessException("私钥不能为空");
        if (isEncrypted(plain)) return plain;
        SecretKeySpec secret = key();
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(PREFIX.getBytes(StandardCharsets.UTF_8));
            byte[] data = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + data.length).put(nonce).put(data).array());
        } catch (Exception e) { throw new BusinessException("私钥加密失败"); }
    }

    public String decrypt(String encrypted) {
        if (!isEncrypted(encrypted)) throw new BusinessException("历史明文密钥须先完成加密迁移");
        SecretKeySpec secret = key();
        try {
            byte[] data = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] nonce = new byte[12]; buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()]; buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(PREFIX.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new BusinessException("私钥无法解密，请核对主密钥或密文完整性"); }
    }
}
