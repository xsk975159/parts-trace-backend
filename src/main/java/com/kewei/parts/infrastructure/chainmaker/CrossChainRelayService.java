package com.kewei.parts.infrastructure.chainmaker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.infrastructure.storage.*;
import com.kewei.parts.module.parts.entity.CrossChainRecord;
import com.kewei.parts.module.parts.mapper.CrossChainRecordMapper;
import org.springframework.beans.factory.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;

/** 持久化中继任务：0待执行、1真实确认、2模拟、3执行中、-1失败。 */
@Service
public class CrossChainRelayService {
    @Autowired private com.kewei.parts.infrastructure.chain.ChainSdkRouter sdkRouter;
    @Autowired private CrossChainRecordMapper mapper;
    @Value("${parts.trace.storage.chain-mode:db}") private String mode;
    public void relayToMasterChain(CrossChainAnchorPayload p) {
        if(p.getPartsDid()==null || p.getPartsDid().isEmpty()) throw new IllegalArgumentException("跨链任务缺少主体DID");
        CrossChainRecord record=new CrossChainRecord();record.setSourceChainId(p.getSourceChainId());record.setSourceTxHash(p.getSourceTxHash());
        record.setSourceBlockHeight(p.getSourceBlockHeight());record.setBusinessType(p.getBusinessType());record.setBusinessId(p.getBusinessId());
        record.setPartsDid(p.getPartsDid());record.setDataHash(p.getDataHash());record.setObjectCid(p.getObjectCid());
        record.setTargetChainId(ChainRouter.MAIN_REGULATOR);record.setAttempts(0);record.setNextRetryAt(new Date());
        record.setStatus("db".equals(mode)?2:0);
        if("db".equals(mode)) { record.setTargetTxHash("sim-main-"+UUID.randomUUID());record.setTargetBlockHeight(mapper.selectMaxTargetBlockHeight()+1);record.setRelayTime(new Date()); }
        try { mapper.insert(record); } catch(DuplicateKeyException e) {
            CrossChainRecord old=mapper.selectOne(new LambdaQueryWrapper<CrossChainRecord>().eq(CrossChainRecord::getSourceChainId,p.getSourceChainId()).eq(CrossChainRecord::getSourceTxHash,p.getSourceTxHash()));
            if(old==null || !Objects.equals(old.getDataHash(),p.getDataHash()) || !Objects.equals(old.getPartsDid(),p.getPartsDid()) || !Objects.equals(old.getBusinessId(),p.getBusinessId()))
                throw new IllegalStateException("相同源交易对应不同业务内容",e);
        }
    }
    @Scheduled(fixedDelayString="${chainmaker.relay-interval-ms:10000}")
    public void processPending() {
        if(!com.kewei.parts.infrastructure.chain.ChainModes.isReal(mode)) return;
        List<CrossChainRecord> records=mapper.selectList(new LambdaQueryWrapper<CrossChainRecord>().in(CrossChainRecord::getStatus,0,-1,3)
            .lt(CrossChainRecord::getAttempts,10).and(w->w.isNull(CrossChainRecord::getNextRetryAt).or().le(CrossChainRecord::getNextRetryAt,new Date()))
            .orderByAsc(CrossChainRecord::getId).last("LIMIT 50"));
        for(CrossChainRecord record:records) {
            if(mapper.claim(record.getId())!=1) continue;
            record.setAttempts(record.getAttempts()+1);
            try {
                ChainStorageAdapter.StoreChainResult receipt=sdkRouter.relay(CrossChainAnchorPayload.builder()
                    .sourceChainId(record.getSourceChainId()).sourceTxHash(record.getSourceTxHash()).sourceBlockHeight(record.getSourceBlockHeight())
                    .businessType(record.getBusinessType()).businessId(record.getBusinessId()).partsDid(record.getPartsDid()).dataHash(record.getDataHash()).objectCid(record.getObjectCid()).build());
                record.setTargetTxHash(receipt.getTxId());record.setTargetBlockHeight(receipt.getBlockHeight());record.setRelayTime(new Date());
                record.setStatus(1);record.setLastError("");
            } catch(Exception e) {
                record.setStatus(-1);String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                record.setLastError(message.substring(0,Math.min(message.length(),1000)));
                record.setNextRetryAt(new Date(System.currentTimeMillis()+Math.min(300000L,10000L*(1L<<Math.min(record.getAttempts(),5)))));
            }
            mapper.updateById(record);
        }
    }
}
