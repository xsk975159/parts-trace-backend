package com.kewei.parts.infrastructure.storage;

public interface ObjectStorageAdapter {

    StoreObjectResult store(String businessType, Long businessId, String content);

    class StoreObjectResult {
        private final String cid;
        private final String contentHash;

        public StoreObjectResult(String cid, String contentHash) {
            this.cid = cid;
            this.contentHash = contentHash;
        }

        public String getCid() {
            return cid;
        }

        public String getContentHash() {
            return contentHash;
        }
    }
}
