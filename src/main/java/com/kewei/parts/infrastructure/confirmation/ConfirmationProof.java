package com.kewei.parts.infrastructure.confirmation;

import com.alibaba.fastjson.JSON;
import com.kewei.parts.infrastructure.chainmaker.*;
import com.kewei.parts.utils.RsaUtil;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 独立的签名域，防止把节点投票当作协调端请求或旧中继签名使用。 */
public final class ConfirmationProof {
    private ConfirmationProof() {}
    public static Map<String, String> values(CrossChainAnchorPayload p, String version, long issuedAt) {
        if (p == null || version == null || !version.matches("[1-9][0-9]{0,8}") || issuedAt <= 0
                || p.getSourceChainId() == null || !p.getSourceChainId().matches("sub-[a-zA-Z0-9_-]{1,60}")
                || p.getSourceTxHash() == null || p.getSourceTxHash().isEmpty() || p.getSourceTxHash().length() > 256
                || p.getSourceBlockHeight() == null || p.getSourceBlockHeight() < 1
                || p.getBusinessType() == null || !p.getBusinessType().matches("[a-zA-Z0-9_-]{1,64}")
                || p.getBusinessId() == null || p.getBusinessId() < 1
                || p.getPartsDid() == null || !p.getPartsDid().matches("did:[a-zA-Z0-9:_-]{5,250}")
                || p.getDataHash() == null || !p.getDataHash().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("跨链核验请求的版本、来源或业务字段不合法");
        Map<String, String> values = RelayProof.values(p, issuedAt);
        values.put("committee_version", version);
        values.put("proof_type", "relay-quorum/v1");
        for (String value : values.values()) if (value == null || value.length() > 1024 || value.contains("\r") || value.contains("\n"))
            throw new IllegalArgumentException("跨链核验字段不合法");
        return values;
    }
    public static String message(CrossChainAnchorPayload p, String version, long issuedAt) {
        return RelayProof.message(values(p, version, issuedAt));
    }
    public static String requestMessage(RelayCertificate.Request request) {
        return "relay-endorsement-request/v1\n" + message(request.getPayload(), request.getCommitteeVersion(), request.getIssuedAt());
    }
    public static void checkTime(long issuedAt, long now) {
        if (issuedAt < now - 300 || issuedAt > now + 60) throw new IllegalArgumentException("核验请求已过期或时钟异常");
    }
    public static boolean verifyVote(RelayCertificate.Vote vote, String expectedId, RelayCertificate.Request request, RelayConfirmationProperties properties) {
        try {
            RelayConfirmationProperties.Member member = properties.getMembers().get(expectedId);
            return vote != null && expectedId.equals(vote.getNodeId()) && member != null
                    && vote.getSignature() != null && vote.getSignature().length() <= 1024
                    && RsaUtil.verify(message(request.getPayload(), request.getCommitteeVersion(), request.getIssuedAt()), vote.getSignature(), member.getPublicKey());
        } catch (Exception error) { return false; }
    }
    public static void verifyCertificate(RelayCertificate certificate, RelayConfirmationProperties properties, long now) {
        if (certificate == null || !properties.getCommitteeVersion().equals(certificate.getCommitteeVersion())) throw new IllegalArgumentException("核验委员会版本不匹配");
        checkTime(certificate.getIssuedAt(), now);
        if (certificate.getSignatures() == null || certificate.getSignatures().size() < properties.threshold()
                || certificate.getSignatures().size() > properties.getMembers().size()) throw new IllegalArgumentException("有效核验签名不足");
        RelayCertificate.Request request = new RelayCertificate.Request();request.setPayload(certificate.getPayload());
        request.setCommitteeVersion(certificate.getCommitteeVersion());request.setIssuedAt(certificate.getIssuedAt());
        for (Map.Entry<String, String> entry : certificate.getSignatures().entrySet()) {
            RelayCertificate.Vote vote = new RelayCertificate.Vote();vote.setNodeId(entry.getKey());vote.setSignature(entry.getValue());
            if (!verifyVote(vote, entry.getKey(), request, properties)) throw new IllegalArgumentException("核验签名不合法");
        }
    }
    public static boolean sameContent(CrossChainAnchorPayload a, CrossChainAnchorPayload b) {
        return values(a, "1", 1).equals(values(b, "1", 1));
    }
    public static Map<String, byte[]> arguments(RelayCertificate certificate) {
        Map<String, byte[]> args = new HashMap<>();
        values(certificate.getPayload(), certificate.getCommitteeVersion(), certificate.getIssuedAt()).forEach((k,v)->args.put(k,v.getBytes(StandardCharsets.UTF_8)));
        List<String> ids = new ArrayList<>(new TreeMap<>(certificate.getSignatures()).keySet());
        List<String> signatures = new ArrayList<>();for (String id : ids) signatures.add(certificate.getSignatures().get(id));
        args.put("validator_ids", JSON.toJSONBytes(ids));args.put("validator_signatures", JSON.toJSONBytes(signatures));
        return args;
    }
}
