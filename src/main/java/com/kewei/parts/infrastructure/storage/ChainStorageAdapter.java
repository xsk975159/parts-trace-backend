package com.kewei.parts.infrastructure.storage;

public interface ChainStorageAdapter {

    /**
     * 兼容旧调用：仅业务类型、主键、哈希。
     */
    default StoreChainResult storeHash(String businessType, Long businessId, String dataHash) {
        return storeAnchor(ChainAnchorRequest.minimal(businessType, businessId, dataHash));
    }

    /**
     * 完整锚点参数，与 Go 合约 {@code save_anchor} 对齐。
     */
    StoreChainResult storeAnchor(ChainAnchorRequest request);

    /**
     * VC 签发后与 DID 关联（合约 {@code append_vc}）；默认无操作。
     */
    default void appendVcIfSupported(String partsDid, String vcId, String vcHash) {
    }

    class StoreChainResult {
        private final String txId;
        private final Long blockHeight;

        public StoreChainResult(String txId, Long blockHeight) {
            this.txId = txId;
            this.blockHeight = blockHeight;
        }

        public String getTxId() {
            return txId;
        }

        public Long getBlockHeight() {
            return blockHeight;
        }
    }
}
