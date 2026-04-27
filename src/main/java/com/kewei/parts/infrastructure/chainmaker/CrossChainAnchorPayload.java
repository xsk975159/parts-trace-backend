package com.kewei.parts.infrastructure.chainmaker;

import lombok.Builder;
import lombok.Value;

/**
 * 主链接收从链摘要，对应合约方法 {@code receive_cross_anchor}（5.2 原型）。
 */
@Value
@Builder
public class CrossChainAnchorPayload {

    String sourceChainId;
    String sourceTxHash;
    String partsDid;
    String dataHash;
    String objectCid;
}
