package com.kewei.parts.module.anomaly.chain;

import com.kewei.parts.module.anomaly.entity.AnomalyAlert;
import com.kewei.parts.module.anomaly.entity.AnomalyRule;
import org.springframework.stereotype.Component;

/**
 * 默认空实现：未接入链上合约时不做任何事。
 * 接入长安链等实现时，可新增 {@code @Primary} 或按 Spring Profile 切换实现类。
 */
@Component
public class NoopAnomalyChainContractHook implements AnomalyChainContractHook {

    @Override
    public void afterRuleMutation(AnomalyRule rule, String action) {
        // 预留：调用 ChainMaker SDK / 合约
    }

    @Override
    public void afterAlertHandled(AnomalyAlert alert) {
        // 预留：上链存证处理结果或发出合约事件
    }
}
