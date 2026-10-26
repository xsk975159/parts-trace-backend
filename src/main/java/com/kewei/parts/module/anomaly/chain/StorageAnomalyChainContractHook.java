package com.kewei.parts.module.anomaly.chain;

import com.kewei.parts.module.anomaly.entity.AnomalyAlert;
import com.kewei.parts.module.anomaly.entity.AnomalyRule;
import org.springframework.stereotype.Component;

/**
 * 规则与告警处置通过统一存储适配器记录审计存证。
 * 真实模式由持久化任务异步提交，本地模式明确标记为模拟。
 */
@Component
public class StorageAnomalyChainContractHook implements AnomalyChainContractHook {
    @org.springframework.beans.factory.annotation.Autowired private com.kewei.parts.infrastructure.storage.AuditRecorder audits;

    @Override
    public void afterRuleMutation(AnomalyRule rule, String action) {
        audits.record("anomaly_rule_"+action,rule.getId(),rule);
    }

    @Override
    public void afterAlertHandled(AnomalyAlert alert) {
        audits.record("alert_handled",alert.getId(),alert);
    }
}
