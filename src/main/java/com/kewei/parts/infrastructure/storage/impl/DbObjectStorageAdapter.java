package com.kewei.parts.infrastructure.storage.impl;

import com.kewei.parts.infrastructure.storage.ObjectStorageAdapter;
import com.kewei.parts.module.parts.entity.ObjectStorageRecord;
import com.kewei.parts.module.parts.mapper.ObjectStorageRecordMapper;
import com.kewei.parts.utils.DidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "parts.trace.storage", name = "object-mode", havingValue = "db", matchIfMissing = true)
public class DbObjectStorageAdapter implements ObjectStorageAdapter {

    @Autowired
    private ObjectStorageRecordMapper objectStorageRecordMapper;

    @Autowired
    private DidUtil didUtil;

    @Override
    public StoreObjectResult store(String businessType, Long businessId, String content) {
        String normalized = content == null ? "" : content;
        String hash = didUtil.sha256Hex(normalized);
        String cid = "db-cid-" + hash.substring(0, 24) + "-" + UUID.randomUUID().toString().substring(0, 6);
        ObjectStorageRecord record = new ObjectStorageRecord();
        record.setCid(cid);
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setContentHash(hash);
        record.setContent(normalized);
        objectStorageRecordMapper.insert(record);
        return new StoreObjectResult(cid, hash);
    }
}
