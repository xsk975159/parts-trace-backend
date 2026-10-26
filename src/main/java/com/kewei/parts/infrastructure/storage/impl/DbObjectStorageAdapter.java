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
    public String read(String cid) {
        ObjectStorageRecord record = objectStorageRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ObjectStorageRecord>().eq(ObjectStorageRecord::getCid, cid));
        if (record == null || record.getContent() == null || !didUtil.sha256Hex(record.getContent()).equals(record.getContentHash()))
            throw new IllegalStateException("存证原文不存在或内容已变更");
        return record.getContent();
    }

    @Override public StoreObjectResult storeFile(String filename,byte[] bytes) {
        String hash=didUtil.sha256Hex(bytes),cid="db-cid-"+hash+"-"+UUID.randomUUID().toString().substring(0,6);
        ObjectStorageRecord record=new ObjectStorageRecord();record.setCid(cid);record.setBusinessType("attachment");record.setContentHash(hash);
        record.setContent("base64:"+java.util.Base64.getEncoder().encodeToString(bytes));objectStorageRecordMapper.insert(record);
        return new StoreObjectResult(cid,hash);
    }
    @Override public byte[] readFile(String cid) {
        ObjectStorageRecord record=objectStorageRecordMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ObjectStorageRecord>().eq(ObjectStorageRecord::getCid,cid));
        if(record==null || record.getContent()==null || !record.getContent().startsWith("base64:")) throw new IllegalStateException("附件不存在");
        byte[] data=java.util.Base64.getDecoder().decode(record.getContent().substring(7));
        if(!didUtil.sha256Hex(data).equals(record.getContentHash())) throw new IllegalStateException("附件内容已修改");return data;
    }

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
