package com.kewei.parts.infrastructure.confirmation;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.chainmaker.CrossChainAnchorPayload;
import com.kewei.parts.utils.RsaUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

/** 并行独立核验及联合签名门禁，不实现TVBA/MVBA或边界排序协议。 */
@Service
@ConditionalOnProperty(name="relay.confirmation.enabled",havingValue="true")
public class RelayConfirmationService {
    private final RelayConfirmationProperties properties;
    private final ChainNetworkProperties networks;
    private final ChainSdkRouter chains;
    private final RelayConfirmationMapper records;
    private final RelayVoteClient client;
    private ExecutorService executor;
    private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
    public RelayConfirmationService(RelayConfirmationProperties properties, ChainNetworkProperties networks, ChainSdkRouter chains,
                                    RelayConfirmationMapper records, RelayVoteClient client) {
        this.properties=properties;this.networks=networks;this.chains=chains;this.records=records;this.client=client;
    }
    @PostConstruct public void initialize() throws Exception {
        if (!RsaUtil.verify("coordinator-key-check",RsaUtil.sign("coordinator-key-check",networks.getRelayPrivateKey()),properties.getCoordinatorPublicKey()))
            throw new IllegalArgumentException("协调端公私钥不匹配");
        chains.verifyRelayPolicy(properties);
        try { records.selectCount(new LambdaQueryWrapper<RelayConfirmation>().eq(RelayConfirmation::getId,-1L)); }
        catch(Exception error) {throw new IllegalStateException("多机构核验表未就绪，请执行db/V20261026__relay_confirmation.sql",error);}
        int count=properties.getMembers().size();
        executor=new ThreadPoolExecutor(count,count,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(count),r->{
            Thread thread=new Thread(r,"relay-validator-request");thread.setDaemon(true);return thread;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    public RelayCertificate confirm(Long recordId, CrossChainAnchorPayload payload) throws Exception {
        chains.verifyConfirmedSource(payload);
        chains.verifyRelayPolicy(properties);
        RelayConfirmation record=records.findRecord(recordId);
        long now=System.currentTimeMillis()/1000;
        if(record!=null && record.getProofJson()!=null) {
            RelayCertificate old=json.readValue(record.getProofJson(),RelayCertificate.class);
            if(!ConfirmationProof.sameContent(old.getPayload(),payload)) throw new IllegalStateException("已有确认凭据与任务内容冲突");
            if(properties.getCommitteeVersion().equals(old.getCommitteeVersion()) && old.getIssuedAt()>=now-270) {
                ConfirmationProof.verifyCertificate(old,properties,now);return old;
            }
        }
        if(record==null) {
            record=new RelayConfirmation();record.setRecordId(recordId);record.setUpdatedAt(new Date());
            record.setCommitteeVersion(properties.getCommitteeVersion());record.setRequiredVotes(properties.threshold());
            record.setValidVotes(0);record.setState("PENDING");records.insert(record);
        }
        record.setCommitteeVersion(properties.getCommitteeVersion());record.setRequiredVotes(properties.threshold());
        record.setValidVotes(0);record.setState("PENDING");record.setLastError("");save(record);
        RelayCertificate.Request request=new RelayCertificate.Request();request.setPayload(payload);request.setIssuedAt(now);
        request.setCommitteeVersion(properties.getCommitteeVersion());request.setCoordinatorSignature(RsaUtil.sign(ConfirmationProof.requestMessage(request),networks.getRelayPrivateKey()));
        RelayCertificate certificate=new RelayCertificate();certificate.setPayload(payload);certificate.setIssuedAt(now);certificate.setCommitteeVersion(properties.getCommitteeVersion());
        CompletionService<Map.Entry<String,RelayCertificate.Vote>> completions=new ExecutorCompletionService<>(executor);
        List<Future<Map.Entry<String,RelayCertificate.Vote>>> futures=new ArrayList<>();
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(2L*properties.getTimeoutMs()+500);
        try {
            for(Map.Entry<String,RelayConfirmationProperties.Member> entry:properties.getMembers().entrySet()) {
                String id=entry.getKey(), endpoint=entry.getValue().getEndpoint();
                futures.add(completions.submit(()->new AbstractMap.SimpleImmutableEntry<>(id,client.request(endpoint,request,properties.getTimeoutMs()))));
            }
            for(int completed=0;completed<futures.size() && certificate.getSignatures().size()<properties.threshold();completed++) {
                long remaining=deadline-System.nanoTime();if(remaining<=0)break;
                Future<Map.Entry<String,RelayCertificate.Vote>> future=completions.poll(remaining,TimeUnit.NANOSECONDS);if(future==null)break;
                try {
                    Map.Entry<String,RelayCertificate.Vote> reply=future.get();
                    if(ConfirmationProof.verifyVote(reply.getValue(),reply.getKey(),request,properties)) certificate.getSignatures().put(reply.getKey(),reply.getValue().getSignature());
                } catch(ExecutionException ignored) { /* 单节点拒绝、断线或错误响应不计入有效票。 */ }
            }
            record.setValidVotes(certificate.getSignatures().size());
            ConfirmationProof.verifyCertificate(certificate,properties,System.currentTimeMillis()/1000);
            record.setProofJson(json.writeValueAsString(certificate));record.setState("CONFIRMED");record.setLastError("");save(record);
            return certificate;
        } catch(Exception error) {
            if(error instanceof InterruptedException) Thread.currentThread().interrupt();
            record.setValidVotes(certificate.getSignatures().size());record.setState("WAITING");
            record.setLastError("有效核验签名 "+certificate.getSignatures().size()+"/"+properties.threshold()+"，本次未提交主链");save(record);
            throw new IllegalStateException(record.getLastError(),error);
        } finally { for(Future<?> future:futures) if(!future.isDone())future.cancel(true); }
    }
    public void committed(Long recordId) {
        RelayConfirmation record=records.findRecord(recordId);
        if(record==null || record.getProofJson()==null)throw new IllegalStateException("主链回执缺少对应确认凭据");
        record.setState("COMMITTED");record.setLastError("");save(record);
    }
    public void failed(Long recordId, Exception error) {
        RelayConfirmation record=records.findRecord(recordId);if(record==null)return;
        String message=error.getMessage()==null?"跨链处理未完成":error.getMessage();
        record.setLastError(message.substring(0,Math.min(1000,message.length())));save(record);
    }
    private void save(RelayConfirmation record) {record.setUpdatedAt(new Date());records.updateById(record);}
    public Map<Long,RelayConfirmation> summaries(Collection<Long> ids) {
        Map<Long,RelayConfirmation> result=new HashMap<>();if(ids.isEmpty())return result;
        for(RelayConfirmation record:records.selectList(new LambdaQueryWrapper<RelayConfirmation>().in(RelayConfirmation::getRecordId,ids)))result.put(record.getRecordId(),record);
        return result;
    }
    public Map<String,Object> overview() {
        Map<String,Object> result=new LinkedHashMap<>();result.put("enabled",true);result.put("mechanism","MULTI_PARTY_ENDORSEMENT");
        result.put("committeeVersion",properties.getCommitteeVersion());result.put("members",properties.getMembers().size());result.put("requiredVotes",properties.threshold());return result;
    }
    @PreDestroy public void close() {if(executor!=null)executor.shutdownNow();}
}
