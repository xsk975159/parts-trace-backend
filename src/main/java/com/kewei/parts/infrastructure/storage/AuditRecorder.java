package com.kewei.parts.infrastructure.storage;
import com.kewei.parts.module.trace.mapper.AuditRecordMapper;
import com.kewei.parts.security.CurrentActor;
import com.kewei.parts.utils.CanonicalJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class AuditRecorder {
    @Autowired private AuditRecordMapper mapper;
    @Autowired private ObjectStorageAdapter objects;
    @Autowired private ChainStorageAdapter chains;
    @Autowired private CurrentActor actor;
    public void record(String action,Long subjectId,Object payload) {
        Map<String,Object> data=new TreeMap<>();data.put("action",action);data.put("subjectId",subjectId);data.put("operatorId",actor.id());
        data.put("time",new Date());data.put("data",payload);
        AuditRecord record=new AuditRecord();record.setAction(action);record.setSubjectId(subjectId);record.setCreateTime(new Date());mapper.insert(record);
        ObjectStorageAdapter.StoreObjectResult object=objects.store("audit",record.getId(),CanonicalJson.write(data));
        ChainStorageAdapter.StoreChainResult chain=chains.storeAnchor(ChainAnchorRequest.builder().businessType("audit").businessId(record.getId())
            .dataHash(object.getContentHash()).objectCid(object.getCid()).chainId(ChainRouter.MAIN_REGULATOR).build());
        record.setDataHash(object.getContentHash());record.setObjectCid(object.getCid());record.setChainTxId(chain.getTxId());mapper.updateById(record);
    }
}
