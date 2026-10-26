package com.kewei.parts.module.trace.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.module.trace.entity.*;
import com.kewei.parts.module.trace.mapper.*;
import com.kewei.parts.utils.CanonicalJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.*;

/** 稳定的业务原文；链回执、审核状态、告警和后继指针不属于原始事件内容。 */
@Component
public class EventSnapshot {
    @Autowired private TraceProductionMapper productionMapper;
    @Autowired private TraceQualityMapper qualityMapper;
    @Autowired private TraceLogisticsMapper logisticsMapper;
    @Autowired private com.kewei.parts.infrastructure.storage.ObjectStorageAdapter objects;
    @Autowired private com.kewei.parts.utils.DidUtil hashes;

    public String canonical(TraceEvent event) { return CanonicalJson.write(data(event)); }

    public Map<String,Object> data(TraceEvent event) {
        Map<String,Object> payload=new TreeMap<>();
        payload.put("schemaVersion",2);payload.put("previousEventId",event.getPreviousEventId());payload.put("eventCode",event.getEventCode());payload.put("partsId",event.getPartsId());
        payload.put("unitId",event.getUnitId());payload.put("partsDid",event.getPartsDid());payload.put("batchNumber",event.getBatchNumber());
        payload.put("eventType",event.getEventType());payload.put("eventName",event.getEventName());payload.put("eventTime",event.getEventTime());
        payload.put("eventLocation",event.getEventLocation());payload.put("operatorId",event.getOperatorId());payload.put("operatorName",event.getOperatorName());
        payload.put("organization",event.getOrganization());payload.put("evidence",evidence(event.getEvidenceUrls()));payload.put("remark",event.getRemark());
        Object detail;
        if("production".equals(event.getEventType())) detail=productionMapper.selectOne(new LambdaQueryWrapper<TraceProduction>().eq(TraceProduction::getEventId,event.getId()));
        else if("quality".equals(event.getEventType())) detail=qualityMapper.selectOne(new LambdaQueryWrapper<TraceQuality>().eq(TraceQuality::getEventId,event.getId()));
        else if("logistics".equals(event.getEventType())) detail=logisticsMapper.selectOne(new LambdaQueryWrapper<TraceLogistics>().eq(TraceLogistics::getEventId,event.getId()));
        else throw new BusinessException("不支持的事件类型");
        if(detail==null) throw new BusinessException("事件明细缺失，无法存证或核验");
        JSONObject fields=(JSONObject)JSON.toJSON(detail);
        for(String key:Arrays.asList("id","eventId","partsId","createTime","updateTime","deleted")) fields.remove(key);
        payload.put("detail",fields);
        return payload;
    }

    /** 附件只接受内容寻址引用，避免把URL字符串摘要误称为文件摘要。 */
    public List<Map<String,String>> evidence(String references) {
        if(references==null || references.trim().isEmpty()) return Collections.emptyList();
        List<String> items=references.trim().startsWith("[")?JSON.parseArray(references,String.class):Arrays.asList(references.split("[,\\n]"));
        List<Map<String,String>> result=new ArrayList<>();
        if(items.size()>10) throw new BusinessException("每个事件最多10个附件");
        for(String item:items) {
            String cid=item.trim();
            if(!cid.matches("ipfs://[a-zA-Z0-9]{10,200}") && !cid.matches("db-cid-[a-f0-9-]{20,100}"))
                throw new BusinessException("附件须先上传并使用ipfs://CID内容地址，不能仅提交可变URL");
            byte[] bytes=objects.readFile(cid.startsWith("ipfs://")?cid.substring(7):cid);
            Map<String,String> attachment=new TreeMap<>();attachment.put("cid",cid);attachment.put("sha256",hashes.sha256Hex(bytes));result.add(attachment);
        }
        return result;
    }
}
