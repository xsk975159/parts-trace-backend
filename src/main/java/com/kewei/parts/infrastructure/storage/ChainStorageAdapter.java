package com.kewei.parts.infrastructure.storage;

public interface ChainStorageAdapter {

    /**
     * 兼容旧调用：仅业务类型、主键、哈希。
     */
    default StoreChainResult storeHash(String businessType, Long businessId, String dataHash) {
        return storeAnchor(ChainAnchorRequest.minimal(businessType, businessId, dataHash));
    }

    /**
     * 完整锚点参数，由路由映射到长安链或FISCO合约的 {@code save_anchor}。
     */
    StoreChainResult storeAnchor(ChainAnchorRequest request);

    String getAnchorHash(String chainId, String businessType, Long businessId);

    String getVcHash(String partsDid, String vcId);

    void revokeVc(String partsDid, String vcId);

    default boolean isSimulated() { return false; }

    /**
     * VC 签发后与 DID 关联（合约 {@code append_vc}）；默认无操作。
     */
    default void appendVcIfSupported(String partsDid, String vcId, String vcHash) {
    }

    class StoreChainResult {
        private final String txId;
        private final Long blockHeight;
        private final boolean simulated;

        public StoreChainResult(String txId, Long blockHeight) {
            this(txId, blockHeight, false);
        }

        public StoreChainResult(String txId, Long blockHeight, boolean simulated) {
            this.txId = txId; this.blockHeight = blockHeight; this.simulated = simulated;
        }

        public boolean isSimulated() { return simulated; }

        public String getTxId() {
            return txId;
        }

        public Long getBlockHeight() {
            return blockHeight;
        }
    }
}
