package com.kewei.parts.infrastructure.storage.impl;

import com.kewei.parts.infrastructure.chainmaker.CrossChainAnchorPayload;
import com.kewei.parts.infrastructure.chainmaker.CrossChainRelayService;
import com.kewei.parts.infrastructure.storage.ChainAnchorRequest;
import com.kewei.parts.infrastructure.storage.ChainRouter;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import com.kewei.parts.module.parts.entity.BlockchainRecord;
import com.kewei.parts.module.parts.mapper.BlockchainRecordMapper;
import com.kewei.parts.utils.DidUtil;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private ChainRouter chainRouter;

    @Autowired
    private CrossChainRelayService crossChainRelayService;

    @Override public boolean isSimulated() { return true; }
    private Long vcKey(String did, String id) { return Long.parseLong(didUtil.sha256Hex(did + "|" + id).substring(0,15),16); }
    @Override public String getAnchorHash(String chainId, String type, Long id) {
        BlockchainRecord record=blockchainRecordMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BlockchainRecord>()
            .eq(BlockchainRecord::getChainId,chainId).eq(BlockchainRecord::getBusinessType,type).eq(BlockchainRecord::getBusinessId,id)
            .eq(BlockchainRecord::getStatus,2).orderByDesc(BlockchainRecord::getId).last("LIMIT 1"));
        return record==null?null:record.getDataHash();
    }
    @Override public String getVcHash(String did,String id) { return getAnchorHash(ChainRouter.MAIN_REGULATOR,"vc_append",vcKey(did,id)); }
    @Override public void revokeVc(String did,String id) { appendVcIfSupported(did,id,"0"); }

    @Override
    public StoreChainResult storeAnchor(ChainAnchorRequest request) {
        String chainId = request.getChainId() != null ? request.getChainId() : ChainRouter.MAIN_REGULATOR;
        long height = blockchainRecordMapper.selectMaxBlockHeight(chainId) + 1;
        String txId = "sim-tx-" + UUID.randomUUID().toString().replace("-", "");
        BlockchainRecord record = new BlockchainRecord();
        record.setBusinessType(request.getBusinessType());
        record.setBusinessId(request.getBusinessId());
        record.setTxId(txId);
        record.setBlockHeight(height);
        record.setDataHash(request.getDataHash());
        record.setChainId(chainId);
        record.setBlockHash("block-" + didUtil.sha256Hex(txId).substring(0, 24));
        record.setStatus(2);
        blockchainRecordMapper.insert(record);

        // 业务子链存证成功后，中继摘要到主监管链
        if (chainRouter.isBusinessChain(chainId)) {
            crossChainRelayService.relayToMasterChain(CrossChainAnchorPayload.builder()
                    .sourceChainId(chainId)
                    .sourceTxHash(txId)
                    .sourceBlockHeight(height)
                    .businessId(request.getBusinessId()).businessType(request.getBusinessType())
                    .partsDid(request.getPartsDid())
                    .dataHash(request.getDataHash())
                    .objectCid(request.getObjectCid())
                    .build());
        }
        return new StoreChainResult(txId, height, true);
    }

    @Override
    public void appendVcIfSupported(String partsDid, String vcId, String vcHash) {
        if (!StringUtils.hasText(partsDid) || !StringUtils.hasText(vcId) || !StringUtils.hasText(vcHash)) {
            return;
        }
        String chainId = ChainRouter.MAIN_REGULATOR;
        long height = blockchainRecordMapper.selectMaxBlockHeight(chainId) + 1;
        String txId = "sim-vc-append-" + UUID.randomUUID().toString().replace("-", "");
        BlockchainRecord record = new BlockchainRecord();
        record.setBusinessType("vc_append");
        record.setBusinessId(vcKey(partsDid, vcId));
        record.setTxId(txId);
        record.setBlockHeight(height);
        record.setDataHash(vcHash);
        record.setChainId(chainId);
        record.setBlockHash("block-" + didUtil.sha256Hex(txId).substring(0, 24));
        record.setStatus(2);
        blockchainRecordMapper.insert(record);
    }
}
