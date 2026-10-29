package com.kewei.parts.infrastructure.chain;

import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import java.util.Map;

/** 平台差异仅留在SDK和合约编码中，业务服务不直接依赖某个平台。 */
public interface ChainDriver {
    ChainPlatform platform();
    boolean configured(String chainCode);
    String physicalIdentity(String chainCode);
    ChainStorageAdapter.StoreChainResult invoke(String chainCode, String method, Map<String, byte[]> arguments) throws Exception;
    String query(String chainCode, String method, Map<String, byte[]> arguments) throws Exception;
    void verifyAnchorTransaction(String chainCode, com.kewei.parts.infrastructure.chainmaker.CrossChainAnchorPayload payload) throws Exception;
}
