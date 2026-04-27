package com.kewei.parts.module.anomaly.chain;

import com.kewei.parts.module.anomaly.entity.AnomalyAlert;
import com.kewei.parts.module.anomaly.entity.AnomalyRule;

/**
 * 异常检测与区块链智能合约的对接扩展点（预留）。
 * <p>
 * 后续可在此接入长安链等平台的 Wasm/EVM 合约，实现规则/预警哈希上链、
 * 链上可验证告警等能力。默认实现为空操作，不影响现有业务。
 * </p>
 */
public interface AnomalyChainContractHook {

    /**
     * 规则新增、更新、删除或状态变更后回调（可异步上链存证规则摘要）。
     *
     * @param rule   当前规则实体
     * @param action create | update | delete | toggle
     */
    void afterRuleMutation(AnomalyRule rule, String action);

    /**
     * 预警被处理或状态变更后回调（可异步将处理结果哈希写入合约事件）。
     */
    void afterAlertHandled(AnomalyAlert alert);
}
