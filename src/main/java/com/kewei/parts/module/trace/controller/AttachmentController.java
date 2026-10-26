package com.kewei.parts.module.trace.controller;
import com.kewei.parts.common.*;
import com.kewei.parts.infrastructure.storage.ObjectStorageAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {
    @Autowired private ObjectStorageAdapter objects;
    @PostMapping
    public Result<Map<String,Object>> upload(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        if(file.isEmpty() || file.getSize()>10*1024*1024) throw new BusinessException("附件不能为空且不能超过10MB");
        ObjectStorageAdapter.StoreObjectResult stored=objects.storeFile(file.getOriginalFilename(),file.getBytes());
        Map<String,Object> data=new LinkedHashMap<>();data.put("cid",stored.getCid());data.put("sha256",stored.getContentHash());
        data.put("reference",stored.getCid().startsWith("db-cid-")?stored.getCid():"ipfs://"+stored.getCid());return Result.success(data);
    }
}
