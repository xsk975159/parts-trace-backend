package com.kewei.parts.infrastructure.storage;

import lombok.Builder;
import lombok.Value;

/**
 * 与链上合约 {@code save_anchor} 参数对齐的存证请求（扩展字段可选）。
 */
@Value
@Builder
public class ChainAnchorRequest {

    String businessType;
    Long businessId;
    String dataHash;
    /** IPFS CID 或对象存储引用，可与 {@link com.kewei.parts.module.trace.entity.TraceEvent#getAttachmentCid()} 等对应 */
    String objectCid;
    /** 零部件 / 单件 DID */
    String partsDid;
    /** 目标链编码（多链路由结果，见 {@link ChainRouter}）；空则由适配器回退默认链 */
    String chainId;

    public static ChainAnchorRequest minimal(String businessType, Long businessId, String dataHash) {
        return ChainAnchorRequest.builder()
                .businessType(businessType)
                .businessId(businessId)
                .dataHash(dataHash)
                .chainId(ChainRouter.MAIN_REGULATOR)
                .build();
    }
}
