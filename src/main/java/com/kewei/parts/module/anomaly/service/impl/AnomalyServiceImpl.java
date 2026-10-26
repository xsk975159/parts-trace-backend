package com.kewei.parts.module.anomaly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.module.anomaly.chain.AnomalyChainContractHook;
import com.kewei.parts.module.anomaly.entity.AnomalyAlert;
import com.kewei.parts.module.anomaly.entity.AnomalyRule;
import com.kewei.parts.module.anomaly.mapper.AnomalyAlertMapper;
import com.kewei.parts.module.anomaly.mapper.AnomalyRuleMapper;
import com.kewei.parts.module.anomaly.service.AnomalyService;
import com.kewei.parts.module.parts.mapper.PartsInfoMapper;
import com.kewei.parts.module.trace.mapper.TraceEventMapper;
import com.kewei.parts.module.trace.mapper.VerifiableCredentialMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnomalyServiceImpl implements AnomalyService {

    @Autowired
    private AnomalyRuleMapper ruleMapper;

    @Autowired
    private AnomalyAlertMapper alertMapper;

    @Autowired
    private PartsInfoMapper partsInfoMapper;

    @Autowired
    private TraceEventMapper traceEventMapper;

    @Autowired
    private VerifiableCredentialMapper credentialMapper;

    @Autowired
    private AnomalyChainContractHook anomalyChainContractHook;
    @Autowired private com.kewei.parts.security.CurrentActor actor;
    @Autowired private com.kewei.parts.module.anomaly.service.QualitySafetyService safety;
    @Autowired private com.kewei.parts.module.trace.mapper.TraceQualityMapper qualityMapper;
    @Autowired private com.kewei.parts.module.parts.mapper.PartsCategoryMapper categoryMapper;

    // ===== 规则管理 =====

    @Override
    public List<AnomalyRule> getRuleList(String ruleType, Integer status) {
        LambdaQueryWrapper<AnomalyRule> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(ruleType)) wrapper.eq(AnomalyRule::getRuleType, ruleType);
        if (status != null) wrapper.eq(AnomalyRule::getStatus, status);
        wrapper.orderByDesc(AnomalyRule::getCreateTime);
        return ruleMapper.selectList(wrapper);
    }

    @Override
    public AnomalyRule getRuleById(Long id) {
        AnomalyRule rule = ruleMapper.selectById(id);
        if (rule == null) throw new BusinessException("规则不存在");
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRule(AnomalyRule rule) {
        actor.requireRoles("quality");
        com.kewei.parts.module.anomaly.service.QualitySafetyService.validateRule(rule);
        if (StringUtils.hasText(rule.getRuleCode())) {
            LambdaQueryWrapper<AnomalyRule> w = new LambdaQueryWrapper<>();
            w.eq(AnomalyRule::getRuleCode, rule.getRuleCode());
            if (ruleMapper.selectOne(w) != null) throw new BusinessException("规则编码已存在");
        }
        if (rule.getStatus() == null) rule.setStatus(1);
        rule.setCreatorId(actor.id());
        ruleMapper.insert(rule);
        anomalyChainContractHook.afterRuleMutation(rule, "create");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(AnomalyRule rule) {
        actor.requireRoles("quality");
        com.kewei.parts.module.anomaly.service.QualitySafetyService.validateRule(rule);
        if (ruleMapper.selectById(rule.getId()) == null) throw new BusinessException("规则不存在");
        ruleMapper.updateById(rule);
        anomalyChainContractHook.afterRuleMutation(ruleMapper.selectById(rule.getId()), "update");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        actor.requireRoles("quality");
        AnomalyRule existing = ruleMapper.selectById(id);
        if (existing == null) throw new BusinessException("规则不存在");
        ruleMapper.deleteById(id);
        anomalyChainContractHook.afterRuleMutation(existing, "delete");
    }

    // ===== 预警管理 =====

    @Override
    public List<AnomalyAlert> getAlertList(String alertLevel, Integer status) {
        LambdaQueryWrapper<AnomalyAlert> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(alertLevel)) wrapper.eq(AnomalyAlert::getAlertLevel, alertLevel);
        if (status != null) wrapper.eq(AnomalyAlert::getStatus, status);
        wrapper.orderByDesc(AnomalyAlert::getAlertTime);
        return alertMapper.selectList(wrapper);
    }

    @Override
    public AnomalyAlert getAlertById(Long id) {
        AnomalyAlert alert = alertMapper.selectById(id);
        if (alert == null) throw new BusinessException("预警不存在");
        return alert;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAlert(Long id, String handler, String handleResult) {
        actor.requireRoles("quality");
        if(handleResult==null || handleResult.trim().isEmpty()) throw new BusinessException("请填写处置结果");
        AnomalyAlert alert = alertMapper.selectById(id);
        if (alert == null) throw new BusinessException("预警不存在");
        partsInfoMapper.lockById(alert.getPartsId());
        safety.assertReinspected(alert);
        alert.setStatus(1);
        alert.setHandler(actor.user().getUsername());
        alert.setHandleResult(handleResult);
        alert.setHandleTime(new Date());
        alertMapper.updateById(alert);
        anomalyChainContractHook.afterAlertHandled(alertMapper.selectById(id));
    }

    // ===== 统计数据 =====

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 零部件总数
        stats.put("totalParts", partsInfoMapper.selectCount(new LambdaQueryWrapper<>()));

        // 事件总数
        stats.put("totalEvents", traceEventMapper.selectCount(new LambdaQueryWrapper<>()));

        // 凭证总数
        stats.put("totalCertificates", credentialMapper.selectCount(new LambdaQueryWrapper<>()));

        // 预警总数
        long totalAlerts = alertMapper.selectCount(new LambdaQueryWrapper<>());
        stats.put("totalAlerts", totalAlerts);

        // 未处理预警数
        LambdaQueryWrapper<AnomalyAlert> unhandledWrapper = new LambdaQueryWrapper<>();
        unhandledWrapper.eq(AnomalyAlert::getStatus, 0);
        long unhandledAlerts = alertMapper.selectCount(unhandledWrapper);
        stats.put("unhandledAlerts", unhandledAlerts);

        // 今日事件数（简化实现）
        java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        java.util.Date dayStart=java.util.Date.from(today.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
        java.util.Date monthStart=java.util.Date.from(today.withDayOfMonth(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
        stats.put("todayEvents", traceEventMapper.selectCount(new LambdaQueryWrapper<com.kewei.parts.module.trace.entity.TraceEvent>()
                .ge(com.kewei.parts.module.trace.entity.TraceEvent::getEventTime,dayStart)));

        // 本月事件数（简化实现）
        stats.put("monthEvents", traceEventMapper.selectCount(new LambdaQueryWrapper<com.kewei.parts.module.trace.entity.TraceEvent>()
            .ge(com.kewei.parts.module.trace.entity.TraceEvent::getEventTime,monthStart)));

        // 质检通过率（模拟数据）
        Map<String,Object> quality=qualityMapper.monthlyPassRate(monthStart);
        long inspections=((Number)quality.get("total")).longValue();
        long passed=quality.get("passed")==null?0:((Number)quality.get("passed")).longValue();
        stats.put("qualityPassRate",inspections==0?0:Math.round(passed*10000.0/inspections)/100.0);
        stats.put("categoryStats",partsInfoMapper.categoryStatistics());
        stats.put("eventDistribution",traceEventMapper.eventDistribution());
        java.util.Date weekStart=java.util.Date.from(today.minusDays(6).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
        stats.put("eventTrend",traceEventMapper.eventTrend(weekStart));

        return stats;
    }
}
