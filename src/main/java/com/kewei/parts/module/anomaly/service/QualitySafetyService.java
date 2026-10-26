package com.kewei.parts.module.anomaly.service;

import com.alibaba.fastjson.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.module.anomaly.entity.*;
import com.kewei.parts.module.anomaly.mapper.*;
import com.kewei.parts.module.parts.entity.*;
import com.kewei.parts.module.parts.mapper.*;
import com.kewei.parts.module.trace.entity.*;
import com.kewei.parts.module.trace.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class QualitySafetyService {
    @Autowired private PartsDidMapper didMapper;
    @Autowired private PartsInfoMapper partsMapper;
    @Autowired private AnomalyAlertMapper alertMapper;
    @Autowired private AnomalyRuleMapper ruleMapper;
    @Autowired private TraceEventMapper eventMapper;
    @Autowired private TraceQualityMapper qualityMapper;
    @Autowired private com.kewei.parts.infrastructure.storage.AuditRecorder audits;

    public static boolean passed(String value) { return Arrays.asList("合格","qualified","pass").contains(value); }
    public static boolean knownResult(String value) { return passed(value) || Arrays.asList("不合格","unqualified","fail").contains(value); }

    public void afterQuality(TraceEvent event,TraceQuality quality) {
        // 不合格属于基本安全约束，不能通过停用自定义规则关闭。
        if(!passed(quality.getInspectionResult())) alert(event,0L,"质检不合格，冻结同批次单件",true);
        for(AnomalyRule rule:ruleMapper.selectList(new LambdaQueryWrapper<AnomalyRule>().eq(AnomalyRule::getStatus,1).eq(AnomalyRule::getRuleType,"quality"))) {
            validateRule(rule);
            JSONObject condition=JSON.parseObject(rule.getCondition());
            Object actual="inspectionResult".equals(condition.getString("field"))?quality.getInspectionResult():quality.getUnqualifiedQuantity();
            if(matches(actual,condition.getString("operator"),condition.get("value"))) {
                boolean freeze="freeze_batch".equals(JSON.parseObject(rule.getAction()).getString("type"));
                alert(event,rule.getId(),rule.getRuleName(),freeze);
            }
        }
    }

    public static void validateRule(AnomalyRule rule) {
        try {
            if(!"quality".equals(rule.getRuleType())) throw new IllegalArgumentException();
            JSONObject condition=JSON.parseObject(rule.getCondition());JSONObject action=JSON.parseObject(rule.getAction());
            String field=condition.getString("field"),operator=condition.getString("operator");
            if(!Arrays.asList("inspectionResult","unqualifiedQuantity").contains(field) || !Arrays.asList("eq","gt","gte","lt","lte").contains(operator)
                || !Arrays.asList("alert","freeze_batch").contains(action.getString("type")) || condition.get("value")==null) throw new IllegalArgumentException();
            if("inspectionResult".equals(field) && (!"eq".equals(operator) || !knownResult(condition.getString("value")))) throw new IllegalArgumentException();
            if("unqualifiedQuantity".equals(field)) new BigDecimal(condition.getString("value"));
        } catch(Exception e) { throw new BusinessException("质量规则须使用支持的JSON字段与动作，不能输入SQL表达式"); }
    }
    static boolean matches(Object actual,String operator,Object expected) {
        if(actual==null) return false;
        if("eq".equals(operator)) return actual instanceof Number ? new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected.toString()))==0 : String.valueOf(actual).equals(String.valueOf(expected));
        int c=new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected.toString()));
        return "gt".equals(operator)?c>0:"gte".equals(operator)?c>=0:"lt".equals(operator)?c<0:c<=0;
    }
    private void alert(TraceEvent event,Long ruleId,String title,boolean freeze) {
        if(alertMapper.selectCount(new LambdaQueryWrapper<AnomalyAlert>().eq(AnomalyAlert::getEventId,event.getId()).eq(AnomalyAlert::getRuleId,ruleId))>0) return;
        PartsInfo part=partsMapper.selectById(event.getPartsId());
        AnomalyAlert alert=new AnomalyAlert();alert.setEventId(event.getId());alert.setUnitId(event.getUnitId());alert.setPartsId(event.getPartsId());
        alert.setPartsCode(part.getPartsCode());alert.setAlertType("quality");alert.setAlertLevel(freeze?"error":"warning");alert.setTitle(title);
        alert.setRuleId(ruleId);alert.setRuleName(title);alert.setDescription("关联事件："+event.getEventCode());alert.setAlertTime(new Date());alert.setStatus(0);
        alertMapper.insert(alert);
        if(freeze) {
            for(PartsDid did:didMapper.selectList(new LambdaQueryWrapper<PartsDid>().eq(PartsDid::getPartsId,event.getPartsId()).in(PartsDid::getDidStatus,1,2))) {
                did.setDidStatus(2);did.setRemark("质量异常冻结："+event.getEventCode());didMapper.updateById(did);
            }
            event.setAbnormalMark(1);event.setAbnormalReason(title);eventMapper.updateById(event);
        }
        audits.record("quality_alert",alert.getId(),alert);
    }

    public void assertReinspected(AnomalyAlert alert) {
        if(alert.getEventId()==null) return;
        TraceEvent event=eventMapper.selectOne(new LambdaQueryWrapper<TraceEvent>().eq(TraceEvent::getPartsId,alert.getPartsId())
            .eq(TraceEvent::getUnitId,alert.getUnitId()).eq(TraceEvent::getEventType,"quality").orderByDesc(TraceEvent::getId).last("LIMIT 1"));
        TraceQuality quality=event==null?null:qualityMapper.selectOne(new LambdaQueryWrapper<TraceQuality>().eq(TraceQuality::getEventId,event.getId()));
        if(event==null || event.getId()<=alert.getEventId() || quality==null || !passed(quality.getInspectionResult()))
            throw new BusinessException("质量告警须在同一单件追加合格复检后才能关闭");
    }
    public void assertCanUnfreeze(Long partsId) {
        if(alertMapper.selectCount(new LambdaQueryWrapper<AnomalyAlert>().eq(AnomalyAlert::getPartsId,partsId).eq(AnomalyAlert::getStatus,0))>0)
            throw new BusinessException("该批次仍有未处理告警，请复检并关闭告警后再解冻");
    }
}
