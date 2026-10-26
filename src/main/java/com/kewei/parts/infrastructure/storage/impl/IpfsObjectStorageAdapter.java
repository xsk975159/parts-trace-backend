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
    public String read(String cid) {
        if (cid == null || !cid.matches("[a-zA-Z0-9]{10,200}")) throw new IllegalArgumentException("IPFS CID格式不合法");
        String url = String.format("http://%s:%d/api/v0/cat?arg=%s", ipfsHost, ipfsPort, cid);
        try (HttpResponse response = HttpRequest.post(url).timeout(15000).execute()) {
            if (!response.isOk()) throw new IllegalStateException("IPFS原文读取失败: HTTP " + response.getStatus());
            return new String(response.bodyBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override public StoreObjectResult storeFile(String filename,byte[] bytes) {
        try(HttpResponse response=HttpRequest.post(String.format("http://%s:%d/api/v0/add?pin=true",ipfsHost,ipfsPort))
                .timeout(30000).form("file",bytes,"attachment.bin").execute()) {
            if(!response.isOk()) throw new IllegalStateException("附件上传IPFS失败");
            String cid=JSONUtil.parseObj(response.body()).getStr("Hash");
            if(cid==null || !cid.matches("[a-zA-Z0-9]{10,200}")) throw new IllegalStateException("IPFS返回的CID不合法");
            return new StoreObjectResult(cid,didUtil.sha256Hex(bytes));
        }
    }
    @Override public byte[] readFile(String cid) {
        if(cid==null || !cid.matches("[a-zA-Z0-9]{10,200}")) throw new IllegalArgumentException("CID不合法");
        try(HttpResponse response=HttpRequest.post(String.format("http://%s:%d/api/v0/cat?arg=%s",ipfsHost,ipfsPort,cid)).timeout(15000).executeAsync();
            java.io.InputStream stream=response.bodyStream();java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()) {
            if(!response.isOk()) throw new IllegalStateException("附件读取失败");
            byte[] buffer=new byte[8192];int length;
            while((length=stream.read(buffer))!=-1) {
                if(output.size()+length>10*1024*1024) throw new IllegalStateException("附件超过10MB限制");
                output.write(buffer,0,length);
            }
            return output.toByteArray();
        } catch(java.io.IOException e) { throw new IllegalStateException("附件读取失败",e); }
    }

    @Override
    public StoreObjectResult store(String businessType, Long businessId, String content) {
        String normalized = content == null ? "" : content;
        String hash = didUtil.sha256Hex(normalized);
        String url = String.format("http://%s:%d/api/v0/add?pin=true", ipfsHost, ipfsPort);
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        try (HttpResponse response = HttpRequest.post(url)
                .timeout(120_000)
                .form("file", bytes, "payload.json")
                .execute()) {
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
}
