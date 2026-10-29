package com.kewei.parts.infrastructure.confirmation;

import com.kewei.parts.infrastructure.chainmaker.CrossChainAnchorPayload;
import lombok.Data;
import java.util.Map;
import java.util.TreeMap;

@Data
public class RelayCertificate {
    private CrossChainAnchorPayload payload;
    private String committeeVersion;
    private long issuedAt;
    /** 节点ID -> Base64 RSA签名。相同节点只保留一个签名。 */
    private Map<String, String> signatures = new TreeMap<>();

    @Data public static class Request {
        private CrossChainAnchorPayload payload;
        private String committeeVersion;
        private long issuedAt;
        private String coordinatorSignature;
    }
    @Data public static class Vote {
        private String nodeId;
        private String signature;
    }
}
