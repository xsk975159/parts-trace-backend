package com.kewei.parts.module.anomaly.service;

import com.kewei.parts.module.anomaly.entity.AnomalyAlert;
import com.kewei.parts.module.anomaly.entity.AnomalyRule;

import java.util.List;
import java.util.Map;

public interface AnomalyService {

    // ===== 规则管理 =====
    List<AnomalyRule> getRuleList(String ruleType, Integer status);
    AnomalyRule getRuleById(Long id);
    void createRule(AnomalyRule rule);
    void updateRule(AnomalyRule rule);
    void deleteRule(Long id);

    // ===== 预警管理 =====
    List<AnomalyAlert> getAlertList(String alertLevel, Integer status);
    AnomalyAlert getAlertById(Long id);
    void handleAlert(Long id, String handler, String handleResult);

    // ===== 统计数据 =====
    Map<String, Object> getStats();
}





