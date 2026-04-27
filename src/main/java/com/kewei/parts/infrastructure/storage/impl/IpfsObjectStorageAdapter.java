package com.kewei.parts.infrastructure.storage.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.kewei.parts.infrastructure.storage.ObjectStorageAdapter;
import com.kewei.parts.module.parts.entity.ObjectStorageRecord;
import com.kewei.parts.module.parts.mapper.ObjectStorageRecordMapper;
import com.kewei.parts.utils.DidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Kubo IPFS HTTP API（/api/v0/add）对象存储适配器。
 */
@Component
@ConditionalOnProperty(prefix = "parts.trace.storage", name = "object-mode", havingValue = "ipfs")
public class IpfsObjectStorageAdapter implements ObjectStorageAdapter {

    @Value("${ipfs.host:127.0.0.1}")
    private String ipfsHost;

    @Value("${ipfs.port:5001}")
    private int ipfsPort;

    @Autowired
    private ObjectStorageRecordMapper objectStorageRecordMapper;

    @Autowired
    private DidUtil didUtil;

    @Override
    public StoreObjectResult store(String businessType, Long businessId, String content) {
        String normalized = content == null ? "" : content;
        String hash = didUtil.sha256Hex(normalized);
        String url = String.format("http://%s:%d/api/v0/add?pin=true", ipfsHost, ipfsPort);
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        HttpResponse response = HttpRequest.post(url)
                .timeout(120_000)
                .form("file", bytes, "payload.json")
                .execute();
        if (!response.isOk()) {
            throw new IllegalStateException("IPFS add failed: HTTP " + response.getStatus()
                    + " body=" + response.body());
        }
        JSONObject json = JSONUtil.parseObj(response.body());
        String cid = json.getStr("Hash");
        if (cid == null || cid.isEmpty()) {
            throw new IllegalStateException("IPFS add response missing Hash: " + response.body());
        }
        ObjectStorageRecord record = new ObjectStorageRecord();
        record.setCid(cid);
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setContentHash(hash);
        record.setContent(null);
        objectStorageRecordMapper.insert(record);
        return new StoreObjectResult(cid, hash);
    }
}
