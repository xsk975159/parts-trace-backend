package com.kewei.parts.infrastructure.storage.impl;
import com.kewei.parts.infrastructure.chainmaker.*;
import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.storage.*;
import com.kewei.parts.module.parts.entity.BlockchainRecord;
import com.kewei.parts.module.parts.mapper.BlockchainRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 真实模式只接受确认回执；失败向业务层报告，绝不创建模拟成功交易。 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(ChainModes.REAL)
public class MultiChainStorageAdapter implements ChainStorageAdapter {
    @Autowired private ChainSdkRouter sdkRouter;
    @Autowired private BlockchainRecordMapper blockchainRecordMapper;
    @Autowired private CrossChainRelayService relay;
    @Autowired private ChainRouter router;
    @Override public StoreChainResult storeAnchor(ChainAnchorRequest request) {
        try {
            StoreChainResult result=sdkRouter.storeAnchor(request);
            BlockchainRecord record=new BlockchainRecord();record.setBusinessType(request.getBusinessType());record.setBusinessId(request.getBusinessId());
            record.setChainId(request.getChainId());record.setDataHash(request.getDataHash());record.setTxId(result.getTxId());
            record.setBlockHeight(result.getBlockHeight());record.setStatus(1);blockchainRecordMapper.insert(record);
            if(router.isBusinessChain(request.getChainId())) relay.relayToMasterChain(CrossChainAnchorPayload.builder()
                .sourceChainId(request.getChainId()).sourceTxHash(result.getTxId()).sourceBlockHeight(result.getBlockHeight())
                .businessType(request.getBusinessType()).businessId(request.getBusinessId()).partsDid(request.getPartsDid())
                .dataHash(request.getDataHash()).objectCid(request.getObjectCid()).build());
            return result;
        } catch(Exception e) { throw new IllegalStateException("真实上链未完成: "+e.getMessage(),e); }
    }
    @Override public void appendVcIfSupported(String did,String id,String hash) {
        try { sdkRouter.appendVc(did,id,hash); } catch(Exception e) { throw new IllegalStateException("凭证链上登记失败",e); }
    }
    @Override public void revokeVc(String did,String id) {
        try { sdkRouter.revokeVc(did,id); } catch(Exception e) { throw new IllegalStateException("凭证链上撤销失败",e); }
    }
    @Override public String getAnchorHash(String code,String type,Long id) {
        try { return sdkRouter.getAnchorHash(code,type,id); } catch(Exception e) { throw new IllegalStateException("链上存证读取失败",e); }
    }
    @Override public String getVcHash(String did,String id) {
        try { return sdkRouter.getVcHash(did,id); } catch(Exception e) { throw new IllegalStateException("链上凭证状态读取失败",e); }
    }
}
