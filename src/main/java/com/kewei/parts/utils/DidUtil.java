package com.kewei.parts.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DID工具
 */
@Component
public class DidUtil {

    @Value("${did.method}")
    private String didMethod;

    @Value("${did.service-endpoint:http://localhost:8080/api/trace}")
    private String serviceEndpoint;

    @Value("${did.chain-id}")
    private String chainId;

    public String generateDid() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("did:%s:%s:%s", didMethod, chainId, uuid);
    }

    public String generatePartDid(String network, String manufacturerDid, String batchNumber, String serialNumber) {
        String raw = manufacturerDid + "|" + batchNumber + "|" + serialNumber + "|" + System.currentTimeMillis();
        String uniqueIdentifier = sha256Hex(raw).substring(0, 32);
        return String.format("did:industrial:part:%s:%s", network, uniqueIdentifier);
    }

    public String generateDidDocument(String did, String publicKey, Long userId, String username) {
        Map<String, Object> didDoc = new HashMap<>();
        didDoc.put("id", did);
        didDoc.put("@context", "https://www.w3.org/ns/did/v1");

        Map<String, Object> publicKeyInfo = new HashMap<>();
        publicKeyInfo.put("id", did + "#keys-1");
        publicKeyInfo.put("type", "RsaVerificationKey2018");
        publicKeyInfo.put("controller", did);
        publicKeyInfo.put("publicKeyPem", publicKey);
        didDoc.put("publicKey", new Object[]{publicKeyInfo});

        didDoc.put("authentication", new String[]{did + "#keys-1"});

        Map<String, Object> service = new HashMap<>();
        service.put("id", did + "#service-1");
        service.put("type", "PartsTraceService");
        service.put("serviceEndpoint", serviceEndpoint);
        didDoc.put("service", new Object[]{service});

        Map<String, Object> metadata = new HashMap<>();

        metadata.put("created", System.currentTimeMillis());
        didDoc.put("metadata", metadata);

        return JSON.toJSONString(didDoc);
    }

    public JSONObject parseDidDocument(String didDocument) {
        return JSON.parseObject(didDocument);
    }

    public String getPublicKeyFromDidDocument(String didDocument) {
        JSONObject doc = parseDidDocument(didDocument);
        if (doc != null && doc.containsKey("publicKey")) {
            Object publicKeyObj = doc.getJSONArray("publicKey").get(0);
            if (publicKeyObj instanceof JSONObject) {
                return ((JSONObject) publicKeyObj).getString("publicKeyPem");
            }
        }
        return null;
    }

    public boolean validateDidFormat(String did) {
        if (did == null || did.isEmpty()) {
            return false;
        }
        String pattern = "^did:" + didMethod + ":" + chainId + ":[a-f0-9]{32}$";
        return did.matches(pattern);
    }

    public boolean validatePartDidFormat(String did) {
        if (did == null || did.isEmpty()) {
            return false;
        }
        return did.matches("^did:industrial:part:[a-zA-Z0-9_-]+:[a-f0-9]{32}$");
    }

    public String sha256Hex(String value) { return sha256Hex(value.getBytes(StandardCharsets.UTF_8)); }
    public String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String s = Integer.toHexString(b & 0xff);
                if (s.length() == 1) {
                    hex.append('0');
                }
                hex.append(s);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256计算失败", e);
        }
    }
}
