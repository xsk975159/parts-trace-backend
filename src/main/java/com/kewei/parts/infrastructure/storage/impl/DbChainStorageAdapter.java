package com.kewei.parts.infrastructure.storage.impl;

import com.kewei.parts.infrastructure.storage.ChainAnchorRequest;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import com.kewei.parts.module.parts.entity.BlockchainRecord;
import com.kewei.parts.module.parts.mapper.BlockchainRecordMapper;
import com.kewei.parts.utils.DidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "parts.trace.storage", name = "chain-mode", havingValue = "db", matchIfMissing = true)
public class DbChainStorageAdapter implements ChainStorageAdapter {

    @Autowired
    private BlockchainRecordMapper blockchainRecordMapper;

    @Autowired
    private DidUtil didUtil;

    @Value("${chainmaker.chain-id:chain1}")
    private String chainId;

    @Override
    public StoreChainResult storeAnchor(ChainAnchorRequest request) {
        long height = System.currentTimeMillis() / 1000;
        String txId = "tx-" + UUID.randomUUID().toString().replace("-", "");
        BlockchainRecord record = new BlockchainRecord();
        record.setBusinessType(request.getBusinessType());
        record.setBusinessId(request.getBusinessId());
        record.setTxId(txId);
        record.setBlockHeight(height);
        record.setDataHash(request.getDataHash());
        record.setChainId(chainId);
        record.setBlockHash("block-" + didUtil.sha256Hex(txId).substring(0, 24));
        record.setStatus(1);
        blockchainRecordMapper.insert(record);
        return new StoreChainResult(txId, height);
    }

    @Override
    public void appendVcIfSupported(String partsDid, String vcId, String vcHash) {
        if (!StringUtils.hasText(partsDid) || !StringUtils.hasText(vcId) || !StringUtils.hasText(vcHash)) {
            return;
        }
        long height = System.currentTimeMillis() / 1000;
        String txId = "tx-vc-append-" + UUID.randomUUID().toString().replace("-", "");
        BlockchainRecord record = new BlockchainRecord();
        record.setBusinessType("vc_append");
        record.setBusinessId((long) (partsDid + ":" + vcId).hashCode());
        record.setTxId(txId);
        record.setBlockHeight(height);
        record.setDataHash(vcHash);
        record.setChainId(chainId);
        record.setBlockHash("block-" + didUtil.sha256Hex(txId).substring(0, 24));
        record.setStatus(1);
        blockchainRecordMapper.insert(record);
    }
}
