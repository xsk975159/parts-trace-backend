package com.kewei.parts.module.trace.service.impl;

import com.alibaba.fastjson.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.infrastructure.storage.*;
import com.kewei.parts.module.identity.entity.*;
import com.kewei.parts.module.identity.mapper.*;
import com.kewei.parts.module.parts.entity.*;
import com.kewei.parts.module.parts.mapper.*;
import com.kewei.parts.module.system.entity.SysUser;
import com.kewei.parts.module.system.mapper.SysUserMapper;
import com.kewei.parts.module.system.service.RoleService;
import com.kewei.parts.module.trace.entity.*;
import com.kewei.parts.module.trace.mapper.*;
import com.kewei.parts.module.trace.service.*;
import com.kewei.parts.security.*;
import com.kewei.parts.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class VerifiableCredentialServiceImpl implements VerifiableCredentialService {
    @Autowired private VerifiableCredentialMapper credentialMapper;
    @Autowired private TraceEventMapper traceEventMapper;
    @Autowired private PartsDidMapper partsDidMapper;
    @Autowired private PartsInfoMapper partsInfoMapper;
    @Autowired private PartsUnitMapper unitMapper;
    @Autowired private SysUserDidMapper userDidMapper;
    @Autowired private SysUserRsaKeyMapper keyMapper;
    @Autowired private SysUserMapper userMapper;
    @Autowired private RoleService roleService;
    @Autowired private VcVerifyRecordMapper vcVerifyRecordMapper;
    @Autowired private ObjectStorageAdapter objectStorageAdapter;
    @Autowired private ChainStorageAdapter chainStorageAdapter;
    @Autowired private ChainRouter chainRouter;
    @Autowired private CurrentActor actor;
    @Autowired private KeyVault keyVault;
    @Autowired private EventSnapshot snapshots;
    @Autowired private DidUtil didUtil;
    @Autowired private com.kewei.parts.module.parts.service.DidDocumentVerifier documentVerifier;

    @Override @Transactional(rollbackFor=Exception.class)
    public VerifiableCredential issueProductionCertificate(Long eventId) { return issue(eventId,"production","production","manufacturer"); }
    @Override @Transactional(rollbackFor=Exception.class)
    public VerifiableCredential issueQualityReport(Long eventId) { return issue(eventId,"quality","quality","quality"); }
    @Override @Transactional(rollbackFor=Exception.class)
    public VerifiableCredential issueTransferCertificate(Long eventId) { return issue(eventId,"logistics","transfer","logistics"); }

    private VerifiableCredential issue(Long eventId,String eventType,String vcType,String role) {
        actor.requireRoles(role);
        TraceEvent event=traceEventMapper.lockById(eventId);
        if(event==null || !eventType.equals(event.getEventType())) throw new BusinessException("业务事件不存在或类型不符");
        actor.requireSelfOrAdmin(event.getOperatorId());
        VerifiableCredential existing=credentialMapper.selectOne(new LambdaQueryWrapper<VerifiableCredential>()
                .eq(VerifiableCredential::getEventId,eventId).eq(VerifiableCredential::getVcType,vcType));
        if(existing!=null) {
            if(!Integer.valueOf(1).equals(existing.getStatus())) throw new BusinessException("该事件的凭证已撤销，请创建更正事件");
            return existing;
        }
        PartsDid subject=subject(event.getPartsId(),event.getUnitId());
        VerifiableCredential vc=create(subject,vcType);
        vc.setEventId(eventId);
        Map<String,Object> data=new TreeMap<>();data.put("event",snapshots.data(event));data.put("eventHash",event.getEventHash());
        vc.setCredentialData(CanonicalJson.write(data));
        return signAndStore(vc);
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public VerifiableCredential issueComprehensiveCredential(Long partsId) {
        actor.requireRoles("manufacturer");
        List<PartsUnit> units=unitMapper.selectList(new LambdaQueryWrapper<PartsUnit>().eq(PartsUnit::getPartsId,partsId));
        if(units.size()!=1) throw new BusinessException("综合凭证须明确单件，请使用单件综合凭证接口");
        return comprehensive(subject(partsId,units.get(0).getId()));
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public VerifiableCredential issueUnitComprehensiveCredential(Long unitId) {
        actor.requireRoles("manufacturer");
        PartsUnit unit=unitMapper.selectById(unitId);
        if(unit==null) throw new BusinessException("单件不存在");
        return comprehensive(subject(unit.getPartsId(),unitId));
    }
    private VerifiableCredential comprehensive(PartsDid subject) {
        PartsInfo parts=partsInfoMapper.lockById(subject.getPartsId());
        actor.requireSelfOrAdmin(parts.getCreatorId());
        List<TraceEvent> events=traceEventMapper.selectList(new LambdaQueryWrapper<TraceEvent>()
            .eq(TraceEvent::getPartsId,subject.getPartsId()).eq(TraceEvent::getUnitId,subject.getUnitId()).orderByAsc(TraceEvent::getId));
        if(events.isEmpty()) throw new BusinessException("没有可汇总的业务事件");
        List<Object> data=new ArrayList<>();for(TraceEvent event:events) data.add(snapshots.data(event));
        VerifiableCredential vc=create(subject,"comprehensive");
        vc.setCredentialData(CanonicalJson.write(Collections.singletonMap("events",data)));
        return signAndStore(vc);
    }

    private PartsDid subject(Long partsId,Long unitId) {
        if(unitId==null) throw new BusinessException("凭证必须关联明确单件");
        PartsDid did=partsDidMapper.selectOne(new LambdaQueryWrapper<PartsDid>().eq(PartsDid::getPartsId,partsId).eq(PartsDid::getUnitId,unitId));
        if(did==null || (!Integer.valueOf(1).equals(did.getDidStatus()) && !Integer.valueOf(2).equals(did.getDidStatus())))
            throw new BusinessException("单件DID不存在或已归档");
        return did;
    }
    private VerifiableCredential create(PartsDid subject,String type) {
        SysUser current=actor.user();
        SysUserDid issuer=userDidMapper.selectOne(new LambdaQueryWrapper<SysUserDid>().eq(SysUserDid::getUserId,current.getId()).eq(SysUserDid::getBindStatus,1));
        if(issuer==null) throw new BusinessException("签发前须绑定签发方DID及托管密钥");
        VerifiableCredential vc=new VerifiableCredential();vc.setVcId("VC-"+UUID.randomUUID());vc.setVcType(type);
        vc.setPartsId(subject.getPartsId());vc.setUnitId(subject.getUnitId());vc.setSubjectDid(subject.getDid());
        vc.setIssuerDid(issuer.getDid());vc.setIssuerName(current.getRealName()==null?current.getUsername():current.getRealName());
        long now=System.currentTimeMillis()/1000*1000;vc.setIssuanceDate(new Date(now));vc.setExpirationDate(new Date(now+365L*86400000));vc.setStatus(1);
        return vc;
    }
    private VerifiableCredential signAndStore(VerifiableCredential vc) {
        SysUserDid issuer=issuer(vc.getIssuerDid());
        SysUserRsaKey key=keyMapper.selectOne(new LambdaQueryWrapper<SysUserRsaKey>().eq(SysUserRsaKey::getUserId,issuer.getUserId())
            .eq(SysUserRsaKey::getPublicKey,issuer.getPublicKey()).eq(SysUserRsaKey::getStatus,1)
            .and(w->w.isNull(SysUserRsaKey::getExpireTime).or().gt(SysUserRsaKey::getExpireTime,new Date())).orderByDesc(SysUserRsaKey::getId).last("LIMIT 1"));
        if(key==null) throw new BusinessException("签发方DID没有匹配的有效托管私钥");
        try {
            Map<String,Object> proof=new TreeMap<>();proof.put("type","RsaSignature2018");proof.put("verificationMethod",vc.getIssuerDid()+"#keys-1");
            proof.put("signatureValue",RsaUtil.sign(CanonicalJson.write(envelope(vc)),keyVault.decrypt(key.getPrivateKey())));
            vc.setProof(CanonicalJson.write(proof));
        } catch(BusinessException e) { throw e; }
        catch(Exception e) { throw new BusinessException("凭证签名失败"); }
        credentialMapper.insert(vc);
        String raw=signedDocument(vc);String hash=didUtil.sha256Hex(raw);
        ObjectStorageAdapter.StoreObjectResult object=objectStorageAdapter.store("vc",vc.getId(),raw);
        ChainStorageAdapter.StoreChainResult chain=chainStorageAdapter.storeAnchor(ChainAnchorRequest.builder().businessType("vc").businessId(vc.getId())
            .dataHash(hash).objectCid(object.getCid()).partsDid(vc.getSubjectDid()).chainId(chainRouter.vcChain(vc.getVcType())).build());
        JSONObject payload=JSON.parseObject(vc.getCredentialData());payload.put("vcHash",hash);payload.put("vcCid",object.getCid());
        vc.setCredentialData(CanonicalJson.write(payload));vc.setBlockchainTxId(chain.getTxId());credentialMapper.updateById(vc);
        chainStorageAdapter.appendVcIfSupported(vc.getSubjectDid(),vc.getVcId(),hash);
        return vc;
    }

    private SysUserDid issuer(String did) {
        return userDidMapper.selectOne(new LambdaQueryWrapper<SysUserDid>().eq(SysUserDid::getDid,did).eq(SysUserDid::getBindStatus,1));
    }
    private Map<String,Object> envelope(VerifiableCredential vc) {
        JSONObject payload=JSON.parseObject(vc.getCredentialData());payload.remove("vcHash");payload.remove("vcCid");
        Map<String,Object> map=new TreeMap<>();map.put("schemaVersion",2);map.put("vcId",vc.getVcId());map.put("vcType",vc.getVcType());
        map.put("partsId",vc.getPartsId());map.put("unitId",vc.getUnitId());map.put("eventId",vc.getEventId());map.put("issuerDid",vc.getIssuerDid());
        map.put("issuerName",vc.getIssuerName());map.put("subjectDid",vc.getSubjectDid());map.put("issuanceDate",vc.getIssuanceDate());
        map.put("expirationDate",vc.getExpirationDate());map.put("credentialSubject",payload);return map;
    }
    private String signedDocument(VerifiableCredential vc) {
        Map<String,Object> doc=envelope(vc);doc.put("proof",JSON.parseObject(vc.getProof()));return CanonicalJson.write(doc);
    }

    @Override public boolean verifyCredential(String vcId) { return Boolean.TRUE.equals(((Map<?,?>)verifyCredentialStandard(vcId)).get("verified")); }
    @Override public Object verifyCredentialStandard(String vcId) {
        long start=System.currentTimeMillis();List<String> issues=new ArrayList<>();Map<String,Object> result=new LinkedHashMap<>();
        result.put("vcId",vcId);result.put("simulated",chainStorageAdapter.isSimulated());
        for(String check:Arrays.asList("statusCheck","expirationCheck","signatureCheck","hashCheck","ownershipCheck")) result.put(check,false);
        VerifiableCredential vc=credentialMapper.selectOne(new LambdaQueryWrapper<VerifiableCredential>().eq(VerifiableCredential::getVcId,vcId));
        if(vc==null) issues.add("凭证不存在");
        else {
            result.put("credential",vc);
            result.put("statusCheck",Integer.valueOf(1).equals(vc.getStatus()));
            result.put("expirationCheck",vc.getIssuanceDate()!=null && !vc.getIssuanceDate().after(new Date()) && vc.getExpirationDate()!=null && vc.getExpirationDate().after(new Date()));
            try {
                SysUserDid issuer=issuer(vc.getIssuerDid());SysUser account=issuer==null?null:userMapper.selectById(issuer.getUserId());
                JSONObject proof=JSON.parseObject(vc.getProof());
                boolean eligible=account!=null && Integer.valueOf(1).equals(account.getStatus()) && ("regulator".equals(account.getUserType())
                    || roleService.hasRole(account.getId(),"admin") || roleService.hasRole(account.getId(),role(vc.getVcType())));
                result.put("signatureCheck",eligible && "RsaSignature2018".equals(proof.getString("type"))
                    && (vc.getIssuerDid()+"#keys-1").equals(proof.getString("verificationMethod"))
                    && RsaUtil.verify(CanonicalJson.write(envelope(vc)),proof.getString("signatureValue"),issuer.getPublicKey()));
                PartsDid subject=subject(vc.getPartsId(),vc.getUnitId());
                boolean ownership=Objects.equals(subject.getDid(),vc.getSubjectDid()) && documentVerifier.verify(subject);
                if(vc.getEventId()!=null) {
                    TraceEvent event=traceEventMapper.selectById(vc.getEventId());
                    ownership=ownership && event!=null && Integer.valueOf(1).equals(event.getStatus()) && Objects.equals(event.getPartsId(),vc.getPartsId())
                        && Objects.equals(event.getUnitId(),vc.getUnitId()) && Objects.equals(event.getPartsDid(),vc.getSubjectDid())
                        && snapshots.canonical(event).equals(CanonicalJson.write(JSON.parseObject(vc.getCredentialData()).get("event")));
                }
                result.put("ownershipCheck",ownership);
                String raw=signedDocument(vc);String hash=didUtil.sha256Hex(raw);JSONObject meta=JSON.parseObject(vc.getCredentialData());
                boolean hashCheck=hash.equals(meta.getString("vcHash"))
                    && hash.equals(chainStorageAdapter.getAnchorHash(chainRouter.vcChain(vc.getVcType()),"vc",vc.getId()))
                    && raw.equals(objectStorageAdapter.read(meta.getString("vcCid")));
                result.put("hashCheck",hashCheck);
                String registered=chainStorageAdapter.getVcHash(vc.getSubjectDid(),vc.getVcId());
                result.put("statusCheck",Boolean.TRUE.equals(result.get("statusCheck")) && hash.equals(registered));
            } catch(Exception e) { issues.add("签名、主体或存证读取未通过；历史示例凭证须按新格式重新签发"); }
        }
        for(String check:Arrays.asList("statusCheck","expirationCheck","signatureCheck","hashCheck","ownershipCheck"))
            if(!Boolean.TRUE.equals(result.get(check))) issues.add("statusCheck".equals(check)?"凭证已失效、已撤销或链上登记尚未确认":
                "expirationCheck".equals(check)?"签发时间或有效期不合法":"signatureCheck".equals(check)?"签发方资格或签名验证失败":
                "hashCheck".equals(check)?"凭证原文与存证摘要不一致或无法读取":"凭证主体、DID文档或关联事件不一致");
        boolean verified=issues.isEmpty();result.put("verified",verified);result.put("issues",issues);
        result.put("reason",verified?(chainStorageAdapter.isSimulated()?"密码学核验通过，存证来源为本地模拟账本":"校验通过"):String.join("；",issues));
        long duration=System.currentTimeMillis()-start;result.put("durationMs",duration);
        VcVerifyRecord record=new VcVerifyRecord();record.setVcId(vcId);record.setVerifyResult(verified?1:0);record.setDurationMs(duration);record.setSource("api");
        if(!verified) record.setFailReason(String.join("；",issues));vcVerifyRecordMapper.insert(record);
        return result;
    }
    private String role(String vcType) { return "quality".equals(vcType)?"quality":"transfer".equals(vcType)?"logistics":"manufacturer"; }

    @Override @Transactional(rollbackFor=Exception.class)
    public void revokeCredential(String vcId,String reason) {
        if(reason==null || reason.trim().isEmpty()) throw new BusinessException("撤销原因不能为空");
        VerifiableCredential vc=credentialMapper.selectOne(new LambdaQueryWrapper<VerifiableCredential>().eq(VerifiableCredential::getVcId,vcId));
        if(vc==null) throw new BusinessException("凭证不存在");
        SysUserDid issuer=issuer(vc.getIssuerDid());
        if(issuer==null) actor.requireRoles("admin");else actor.requireSelfOrAdmin(issuer.getUserId());
        chainStorageAdapter.revokeVc(vc.getSubjectDid(),vcId);
        vc.setStatus(0);vc.setRevocationReason(reason);credentialMapper.updateById(vc);
    }
    @Override public List<VerifiableCredential> getPartsCredentials(Long id) { return credentialMapper.selectList(new LambdaQueryWrapper<VerifiableCredential>().eq(VerifiableCredential::getPartsId,id).orderByDesc(VerifiableCredential::getIssuanceDate)); }
    @Override public VerifiableCredential getEventCredential(Long id) { return credentialMapper.selectOne(new LambdaQueryWrapper<VerifiableCredential>().eq(VerifiableCredential::getEventId,id)); }
    @Override public List<VcVerifyRecord> getVerifyRecords(String id) { return vcVerifyRecordMapper.selectList(new LambdaQueryWrapper<VcVerifyRecord>().eq(id!=null && !id.trim().isEmpty(),VcVerifyRecord::getVcId,id).orderByDesc(VcVerifyRecord::getCreateTime).last("LIMIT 500")); }
}
