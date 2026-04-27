package com.kewei.parts.infrastructure.storage;

public interface ChainStorageAdapter {

    StoreChainResult storeHash(String businessType, Long businessId, String dataHash);

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
