package com.kewei.parts.infrastructure.storage;

import com.alibaba.fastjson.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.mapper.PartsDidMapper;
import com.kewei.parts.module.trace.entity.*;
import com.kewei.parts.module.trace.mapper.*;
import com.kewei.parts.utils.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;

/** 与业务数据同事务保存任务，提交后再调用外部链。没有回执时txId保持空值。 */
@Primary
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(com.kewei.parts.infrastructure.chain.ChainModes.REAL)
public class DurableChainStorageAdapter implements ChainStorageAdapter {
    @Autowired @Qualifier("multiChainStorageAdapter") private ChainStorageAdapter delegate;
    @Autowired private ChainTaskMapper tasks;
    @Autowired private TraceEventMapper events;
    @Autowired private VerifiableCredentialMapper credentials;
    @Autowired private PartsDidMapper dids;
    @Autowired private AuditRecordMapper audits;
    @Autowired private DidUtil hashes;

    private void enqueue(String action,String key,Object payload) {
        String taskKey=hashes.sha256Hex(action+"|"+key), raw=CanonicalJson.write(payload);
        ChainTask old=tasks.selectOne(new LambdaQueryWrapper<ChainTask>().eq(ChainTask::getTaskKey,taskKey));
        if(old!=null) {
            if(!raw.equals(old.getPayload())) throw new IllegalStateException("同一链任务编号不能对应不同内容");
            return;
        }
        ChainTask task=new ChainTask();task.setTaskKey(taskKey);task.setAction(action);task.setPayload(raw);task.setStatus(0);
        task.setAttempts(0);task.setNextRetryAt(new Date());task.setCreateTime(new Date());tasks.insert(task);
    }
    @Override public StoreChainResult storeAnchor(ChainAnchorRequest request) {
        enqueue("anchor",request.getChainId()+"|"+request.getBusinessType()+"|"+request.getBusinessId(),request);
        return new StoreChainResult(null,null);
    }
    private Map<String,String> vcPayload(String did,String id,String hash) {
        Map<String,String> payload=new TreeMap<>();payload.put("did",did);payload.put("id",id);payload.put("hash",hash);return payload;
    }
    @Override public void appendVcIfSupported(String did,String id,String hash) { enqueue("append",did+"|"+id,vcPayload(did,id,hash)); }
    @Override public void revokeVc(String did,String id) { enqueue("revoke",did+"|"+id,vcPayload(did,id,null)); }
    @Override public String getAnchorHash(String code,String type,Long id) { return delegate.getAnchorHash(code,type,id); }
    @Override public String getVcHash(String did,String id) { return delegate.getVcHash(did,id); }

    @Scheduled(fixedDelayString="${chainmaker.anchor-interval-ms:10000}")
    public void processPending() {
        List<ChainTask> pending=tasks.selectList(new LambdaQueryWrapper<ChainTask>().in(ChainTask::getStatus,0,-1,3).lt(ChainTask::getAttempts,10)
            .and(w->w.isNull(ChainTask::getNextRetryAt).or().le(ChainTask::getNextRetryAt,new Date())).orderByAsc(ChainTask::getId).last("LIMIT 50"));
        for(ChainTask task:pending) {
            if(tasks.claim(task.getId())!=1) continue;
            task.setAttempts(task.getAttempts()+1);JSONObject payload=JSON.parseObject(task.getPayload());
            try {
                if("anchor".equals(task.getAction())) {
                    ChainAnchorRequest request=ChainAnchorRequest.builder().chainId(payload.getString("chainId")).businessType(payload.getString("businessType"))
                        .businessId(payload.getLong("businessId")).dataHash(payload.getString("dataHash")).objectCid(payload.getString("objectCid")).partsDid(payload.getString("partsDid")).build();
                    StoreChainResult receipt=delegate.storeAnchor(request);task.setTxId(receipt.getTxId());publishReceipt(request,receipt);
                } else if("append".equals(task.getAction())) {
                    VerifiableCredential vc=credentials.selectOne(new LambdaQueryWrapper<VerifiableCredential>().eq(VerifiableCredential::getVcId,payload.getString("id")));
                    // 已撤销的凭证不可因旧追加任务延迟执行而恢复有效。
                    if(vc!=null && Integer.valueOf(1).equals(vc.getStatus())) delegate.appendVcIfSupported(payload.getString("did"),payload.getString("id"),payload.getString("hash"));
                } else if("revoke".equals(task.getAction())) delegate.revokeVc(payload.getString("did"),payload.getString("id"));
                else throw new IllegalStateException("不支持的链任务");
                task.setStatus(1);task.setLastError("");
            } catch(Exception e) {
                task.setStatus(-1);String error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                task.setLastError(error.substring(0,Math.min(error.length(),1000)));
                task.setNextRetryAt(new Date(System.currentTimeMillis()+Math.min(300000L,10000L*(1L<<Math.min(task.getAttempts(),5)))));
                if("anchor".equals(task.getAction()) && "trace_event".equals(payload.getString("businessType"))) {
                    TraceEvent update=new TraceEvent();update.setId(payload.getLong("businessId"));update.setChainStatus(-1);events.updateById(update);
                }
            }
            tasks.updateById(task);
        }
    }
    private void publishReceipt(ChainAnchorRequest request,StoreChainResult receipt) {
        Long id=request.getBusinessId();
        if("trace_event".equals(request.getBusinessType())) {
            TraceEvent update=new TraceEvent();update.setId(id);update.setBlockchainTxId(receipt.getTxId());update.setChainStatus(1);events.updateById(update);
        } else if("vc".equals(request.getBusinessType())) {
            VerifiableCredential update=new VerifiableCredential();update.setId(id);update.setBlockchainTxId(receipt.getTxId());credentials.updateById(update);
        } else if("did_document_unit".equals(request.getBusinessType())) {
            PartsDid did=dids.selectOne(new LambdaQueryWrapper<PartsDid>().eq(PartsDid::getUnitId,id));
            if(did==null) throw new IllegalStateException("单件DID尚未提交");
            did.setChainTxId(receipt.getTxId());did.setBlockHeight(receipt.getBlockHeight());dids.updateById(did);
        } else if("audit".equals(request.getBusinessType())) {
            AuditRecord update=new AuditRecord();update.setId(id);update.setChainTxId(receipt.getTxId());audits.updateById(update);
        }
    }
}
