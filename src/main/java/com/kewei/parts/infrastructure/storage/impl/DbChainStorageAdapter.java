package com.kewei.parts.infrastructure.storage.impl;

import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import com.kewei.parts.module.parts.entity.BlockchainRecord;
import com.kewei.parts.module.parts.mapper.BlockchainRecordMapper;
import com.kewei.parts.utils.DidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DbChainStorageAdapter implements ChainStorageAdapter {

    @Autowired
    private BlockchainRecordMapper blockchainRecordMapper;

    @Autowired
    private DidUtil didUtil;

    @Value("${chainmaker.chain-id:chain1}")
    private String chainId;

    @Override
    public StoreChainResult storeHash(String businessType, Long businessId, String dataHash) {
        long height = System.currentTimeMillis() / 1000;
        String txId = "tx-" + UUID.randomUUID().toString().replace("-", "");
        BlockchainRecord record = new BlockchainRecord();
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setTxId(txId);
        record.setBlockHeight(height);
        record.setDataHash(dataHash);
        record.setChainId(chainId);
        record.setBlockHash("block-" + didUtil.sha256Hex(txId).substring(0, 24));
        record.setStatus(1);
        blockchainRecordMapper.insert(record);
        return new StoreChainResult(txId, height);
    }
}
