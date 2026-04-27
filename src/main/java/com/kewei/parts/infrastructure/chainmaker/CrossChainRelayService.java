package com.kewei.parts.infrastructure.chainmaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 跨链摘要中继：在从链交易确认后，由调度/网关调用，向主链写入 {@code receive_cross_anchor}。
 * <p>未启用 SDK 时仅记录日志，不抛错，便于无链环境开发。</p>
 */
@Service
public class CrossChainRelayService {

    private static final Logger log = LoggerFactory.getLogger(CrossChainRelayService.class);

    @Autowired(required = false)
    private ChainMakerSdkInvoker sdkInvoker;

    /**
     * 将跨链摘要提交到当前配置的链（论文 5.2 中与主链合约对应）。
     */
    public void relayToMasterChain(CrossChainAnchorPayload payload) {
        if (sdkInvoker == null || !sdkInvoker.isReady()) {
            log.debug("跨链中继跳过：SDK 未启用或未就绪。payload source={} tx={}",
                    payload.getSourceChainId(), payload.getSourceTxHash());
            return;
        }
        try {
            sdkInvoker.invokeReceiveCrossAnchor(payload);
            log.info("跨链摘要已提交主链: sourceTx={}", payload.getSourceTxHash());
        } catch (Exception e) {
            log.error("跨链摘要提交失败: {}", e.getMessage());
        }
    }
}
