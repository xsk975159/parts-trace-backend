package com.kewei.parts.infrastructure.chain;

import com.kewei.parts.infrastructure.storage.ChainAnchorRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ContractArguments {
    private ContractArguments() {}

    public static Map<String, byte[]> anchor(ChainAnchorRequest request) {
        Map<String, byte[]> arguments = anchorKey(request.getBusinessType(), request.getBusinessId());
        put(arguments, "data_hash", request.getDataHash());
        put(arguments, "object_cid", request.getObjectCid());
        put(arguments, "parts_did", request.getPartsDid());
        return arguments;
    }

    public static Map<String, byte[]> anchorKey(String type, Long id) {
        if (type == null || !type.matches("[a-zA-Z0-9_-]{1,64}") || id == null || id < 0)
            throw new IllegalArgumentException("业务存证编号不合法");
        Map<String, byte[]> arguments = new LinkedHashMap<>();
        put(arguments, "business_type", type);
        put(arguments, "business_id", String.valueOf(id));
        return arguments;
    }

    public static Map<String, byte[]> vc(String did, String id, String hash) {
        Map<String, byte[]> arguments = new LinkedHashMap<>();
        put(arguments, "parts_did", did);
        put(arguments, "vc_id", id);
        if (hash != null) put(arguments, "vc_hash", hash);
        return arguments;
    }

    public static String text(Map<String, byte[]> arguments, String key) {
        byte[] value = arguments.get(key);
        return value == null ? "" : new String(value, StandardCharsets.UTF_8);
    }

    public static void put(Map<String, byte[]> arguments, String key, String value) {
        arguments.put(key, (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }
}
