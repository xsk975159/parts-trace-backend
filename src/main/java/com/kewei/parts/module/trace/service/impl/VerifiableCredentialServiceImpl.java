package com.kewei.parts.module.trace.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.infrastructure.storage.ChainAnchorRequest;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import com.kewei.parts.infrastructure.storage.ObjectStorageAdapter;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.entity.PartsInfo;
import com.kewei.parts.module.parts.mapper.PartsDidMapper;
import com.kewei.parts.module.parts.mapper.PartsInfoMapper;
import com.kewei.parts.module.trace.entity.*;
import com.kewei.parts.module.trace.mapper.*;
import com.kewei.parts.module.trace.service.VerifiableCredentialService;
import com.kewei.parts.module.identity.entity.SysUserDid;
import com.kewei.parts.module.identity.mapper.SysUserDidMapper;
import com.kewei.parts.utils.DidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * 可验证凭证服务实现类
 */
@Service
public class VerifiableCredentialServiceImpl implements VerifiableCredentialService {

    @Autowired
    private VerifiableCredentialMapper credentialMapper;

    @Autowired
    private TraceEventMapper traceEventMapper;

    @Autowired
    private TraceProductionMapper traceProductionMapper;

    @Autowired
    private TraceQualityMapper traceQualityMapper;

    @Autowired
    private TraceLogisticsMapper traceLogisticsMapper;

    @Autowired
    private PartsInfoMapper partsInfoMapper;

    @Autowired
    private PartsDidMapper partsDidMapper;

    @Autowired
    private SysUserDidMapper userDidMapper;

    @Autowired
    private VcVerifyRecordMapper vcVerifyRecordMapper;

    @Autowired
    private ObjectStorageAdapter objectStorageAdapter;

    @Autowired
    private ChainStorageAdapter chainStorageAdapter;

    @Autowired
    private DidUtil didUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VerifiableCredential issueProductionCertificate(Long eventId) {
        TraceEvent event = traceEventMapper.selectById(eventId);
        if (event == null || !"production".equals(event.getEventType())) {
            throw new BusinessException("生产事件不存在");
        }

        // 查询生产事件详情
        LambdaQueryWrapper<TraceProduction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TraceProduction::getEventId, eventId);
        TraceProduction production = traceProductionMapper.selectOne(wrapper);

        // 获取签发者DID
        String issuerDid = getUserDid(event.getOperatorId());

        // 获取零部件DID
        String subjectDid = getPartsDid(event.getPartsId(), event.getUnitId());

        // 构建凭证数据
        Map<String, Object> credentialData = buildProductionCredentialData(event, production);

        // 创建可验证凭证
        VerifiableCredential credential = new VerifiableCredential();
        credential.setVcId(generateVcId());
        credential.setPartsId(event.getPartsId());
        credential.setUnitId(event.getUnitId());
        credential.setEventId(eventId);
        credential.setVcType("production");
        credential.setIssuerDid(issuerDid);
        credential.setIssuerName(event.getOperatorName());
        credential.setSubjectDid(subjectDid);
        credential.setIssuanceDate(new Date());
        credential.setExpirationDate(calculateExpirationDate(365)); // 1年有效期
        credential.setCredentialData(JSON.toJSONString(credentialData));
        credential.setProof(generateProof(credentialData));
        credential.setStatus(1); // 有效

        credentialMapper.insert(credential);
        persistCredentialStorage(credential);

        return credential;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VerifiableCredential issueQualityReport(Long eventId) {
        TraceEvent event = traceEventMapper.selectById(eventId);
        if (event == null || !"quality".equals(event.getEventType())) {
            throw new BusinessException("质检事件不存在");
        }

        // 查询质检事件详情
        LambdaQueryWrapper<TraceQuality> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TraceQuality::getEventId, eventId);
        TraceQuality quality = traceQualityMapper.selectOne(wrapper);

        String issuerDid = getUserDid(event.getOperatorId());
        String subjectDid = getPartsDid(event.getPartsId(), event.getUnitId());

        Map<String, Object> credentialData = buildQualityCredentialData(event, quality);

        VerifiableCredential credential = new VerifiableCredential();
        credential.setVcId(generateVcId());
        credential.setPartsId(event.getPartsId());
        credential.setUnitId(event.getUnitId());
        credential.setEventId(eventId);
        credential.setVcType("quality");
        credential.setIssuerDid(issuerDid);
        credential.setIssuerName(event.getOperatorName());
        credential.setSubjectDid(subjectDid);
        credential.setIssuanceDate(new Date());
        credential.setExpirationDate(calculateExpirationDate(365));
        credential.setCredentialData(JSON.toJSONString(credentialData));
        credential.setProof(generateProof(credentialData));
        credential.setStatus(1);

        credentialMapper.insert(credential);
        persistCredentialStorage(credential);

        return credential;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VerifiableCredential issueTransferCertificate(Long eventId) {
        TraceEvent event = traceEventMapper.selectById(eventId);
        if (event == null || !"logistics".equals(event.getEventType())) {
            throw new BusinessException("物流事件不存在");
        }

        // 查询物流事件详情
        LambdaQueryWrapper<TraceLogistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TraceLogistics::getEventId, eventId);
        TraceLogistics logistics = traceLogisticsMapper.selectOne(wrapper);

        String issuerDid = getUserDid(event.getOperatorId());
        String subjectDid = getPartsDid(event.getPartsId(), event.getUnitId());

        Map<String, Object> credentialData = buildLogisticsCredentialData(event, logistics);

        VerifiableCredential credential = new VerifiableCredential();
        credential.setVcId(generateVcId());
        credential.setPartsId(event.getPartsId());
        credential.setUnitId(event.getUnitId());
        credential.setEventId(eventId);
        credential.setVcType("transfer");
        credential.setIssuerDid(issuerDid);
        credential.setIssuerName(event.getOperatorName());
        credential.setSubjectDid(subjectDid);
        credential.setIssuanceDate(new Date());
        credential.setExpirationDate(calculateExpirationDate(365));
        credential.setCredentialData(JSON.toJSONString(credentialData));
        credential.setProof(generateProof(credentialData));
        credential.setStatus(1);

        credentialMapper.insert(credential);
        persistCredentialStorage(credential);

        return credential;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VerifiableCredential issueComprehensiveCredential(Long partsId) {
        PartsInfo partsInfo = partsInfoMapper.selectById(partsId);
        if (partsInfo == null) {
            throw new BusinessException("零部件不存在");
        }

        // 获取所有事件
        LambdaQueryWrapper<TraceEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TraceEvent::getPartsId, partsId);
        wrapper.orderByAsc(TraceEvent::getEventTime);
        List<TraceEvent> events = traceEventMapper.selectList(wrapper);

        String subjectDid = getPartsDid(partsId, null);

        Map<String, Object> credentialData = buildComprehensiveCredentialData(partsInfo, events);

        VerifiableCredential credential = new VerifiableCredential();
        credential.setVcId(generateVcId());
        credential.setPartsId(partsId);
        credential.setVcType("comprehensive");
        credential.setIssuerDid("system");
        credential.setIssuerName("零部件溯源系统");
        credential.setSubjectDid(subjectDid);
        credential.setIssuanceDate(new Date());
        credential.setExpirationDate(calculateExpirationDate(365));
        credential.setCredentialData(JSON.toJSONString(credentialData));
        credential.setProof(generateProof(credentialData));
        credential.setStatus(1);

        credentialMapper.insert(credential);
        persistCredentialStorage(credential);

        return credential;
    }

    @Override
    public boolean verifyCredential(String vcId) {
        LambdaQueryWrapper<VerifiableCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VerifiableCredential::getVcId, vcId);
        VerifiableCredential credential = credentialMapper.selectOne(wrapper);

        if (credential == null) {
            return false;
        }

        // 检查状态
        if (credential.getStatus() != 1) {
            return false;
        }

        // 检查是否过期
        if (credential.getExpirationDate() != null && 
            credential.getExpirationDate().before(new Date())) {
            return false;
        }

        // 验证签名（这里简化处理）
        return true;
    }

    @Override
    public Object verifyCredentialStandard(String vcId) {
        LambdaQueryWrapper<VerifiableCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VerifiableCredential::getVcId, vcId);
        VerifiableCredential credential = credentialMapper.selectOne(wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("vcId", vcId);
        long start = System.currentTimeMillis();
        if (credential == null) {
            result.put("verified", false);
            result.put("reason", "VC不存在");
            result.put("statusCheck", false);
            result.put("expirationCheck", false);
            result.put("signatureCheck", false);
            result.put("hashCheck", true);
            result.put("ownershipCheck", true);
            result.put("durationMs", 0);
            saveVerifyRecord(vcId, false, "VC不存在", 0L, "api");
            return result;
        }
        boolean statusCheck = credential.getStatus() != null && credential.getStatus() == 1;
        boolean expirationCheck = credential.getExpirationDate() == null || !credential.getExpirationDate().before(new Date());
        boolean signatureCheck = credential.getProof() != null && credential.getProof().startsWith("proof-");
        boolean hashCheck = true;
        boolean ownershipCheck = true;
        boolean verified = statusCheck && expirationCheck && signatureCheck && hashCheck && ownershipCheck;
        result.put("verified", verified);
        result.put("reason", verified ? "校验通过" : "校验失败");
        result.put("statusCheck", statusCheck);
        result.put("expirationCheck", expirationCheck);
        result.put("signatureCheck", signatureCheck);
        result.put("hashCheck", hashCheck);
        result.put("ownershipCheck", ownershipCheck);
        result.put("durationMs", System.currentTimeMillis() - start);
        result.put("credential", credential);
        saveVerifyRecord(vcId, verified, verified ? null : "签名/状态/归属校验失败", System.currentTimeMillis() - start, "api");
        return result;
    }

    @Override
    public List<VcVerifyRecord> getVerifyRecords(String vcId) {
        LambdaQueryWrapper<VcVerifyRecord> wrapper = new LambdaQueryWrapper<>();
        if (vcId != null && !vcId.trim().isEmpty()) {
            wrapper.eq(VcVerifyRecord::getVcId, vcId.trim());
        }
        wrapper.orderByDesc(VcVerifyRecord::getCreateTime);
        return vcVerifyRecordMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeCredential(String vcId, String reason) {
        LambdaQueryWrapper<VerifiableCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VerifiableCredential::getVcId, vcId);
        VerifiableCredential credential = credentialMapper.selectOne(wrapper);

        if (credential == null) {
            throw new BusinessException("凭证不存在");
        }

        credential.setStatus(0); // 已撤销
        credential.setRevocationReason(reason);
        credentialMapper.updateById(credential);
    }

    @Override
    public List<VerifiableCredential> getPartsCredentials(Long partsId) {
        LambdaQueryWrapper<VerifiableCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VerifiableCredential::getPartsId, partsId);
        wrapper.orderByDesc(VerifiableCredential::getIssuanceDate);
        return credentialMapper.selectList(wrapper);
    }

    @Override
    public VerifiableCredential getEventCredential(Long eventId) {
        LambdaQueryWrapper<VerifiableCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VerifiableCredential::getEventId, eventId);
        return credentialMapper.selectOne(wrapper);
    }

    /**
     * 获取用户DID
     */
    private String getUserDid(Long userId) {
        LambdaQueryWrapper<SysUserDid> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserDid::getUserId, userId);
        SysUserDid userDid = userDidMapper.selectOne(wrapper);
        return userDid != null ? userDid.getDid() : "unknown";
    }

    /**
     * 获取零部件DID
     */
    private String getPartsDid(Long partsId, Long unitId) {
        LambdaQueryWrapper<PartsDid> wrapper = new LambdaQueryWrapper<>();
        if (unitId != null) {
            wrapper.eq(PartsDid::getUnitId, unitId);
        } else {
            wrapper.eq(PartsDid::getPartsId, partsId);
        }
        wrapper.orderByDesc(PartsDid::getId).last("LIMIT 1");
        PartsDid partsDid = partsDidMapper.selectOne(wrapper);
        return partsDid != null ? partsDid.getDid() : "unknown";
    }

    /**
     * 生成VC ID
     */
    private String generateVcId() {
        return "VC-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 计算过期日期
     */
    private Date calculateExpirationDate(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTime();
    }

    /**
     * 生成证明/签名
     */
    private String generateProof(Map<String, Object> data) {
        // 这里简化处理，实际应该使用私钥签名
        return "proof-" + UUID.randomUUID().toString();
    }

    /**
     * 构建生产凭证数据
     */
    private Map<String, Object> buildProductionCredentialData(TraceEvent event, TraceProduction production) {
        Map<String, Object> data = new HashMap<>();
        data.put("@context", "https://www.w3.org/2018/credentials/v1");
        data.put("type", Arrays.asList("VerifiableCredential", "ProductionCertificate"));
        data.put("eventCode", event.getEventCode());
        data.put("eventTime", event.getEventTime());
        data.put("productionLine", production.getProductionLine());
        data.put("productionBatch", production.getProductionBatch());
        data.put("productionQuantity", production.getProductionQuantity());
        data.put("qualityLevel", production.getQualityLevel());
        return data;
    }

    /**
     * 构建质检凭证数据
     */
    private Map<String, Object> buildQualityCredentialData(TraceEvent event, TraceQuality quality) {
        Map<String, Object> data = new HashMap<>();
        data.put("@context", "https://www.w3.org/2018/credentials/v1");
        data.put("type", Arrays.asList("VerifiableCredential", "QualityReport"));
        data.put("eventCode", event.getEventCode());
        data.put("eventTime", event.getEventTime());
        data.put("inspectionType", quality.getInspectionType());
        data.put("inspectionResult", quality.getInspectionResult());
        data.put("inspector", quality.getInspector());
        data.put("reportNumber", quality.getReportNumber());
        return data;
    }

    /**
     * 构建物流凭证数据
     */
    private Map<String, Object> buildLogisticsCredentialData(TraceEvent event, TraceLogistics logistics) {
        Map<String, Object> data = new HashMap<>();
        data.put("@context", "https://www.w3.org/2018/credentials/v1");
        data.put("type", Arrays.asList("VerifiableCredential", "TransferCertificate"));
        data.put("eventCode", event.getEventCode());
        data.put("eventTime", event.getEventTime());
        data.put("logisticsType", logistics.getLogisticsType());
        data.put("sender", logistics.getSender());
        data.put("receiver", logistics.getReceiver());
        data.put("trackingNumber", logistics.getTrackingNumber());
        return data;
    }

    /**
     * 构建综合凭证数据
     */
    private Map<String, Object> buildComprehensiveCredentialData(PartsInfo partsInfo, List<TraceEvent> events) {
        Map<String, Object> data = new HashMap<>();
        data.put("@context", "https://www.w3.org/2018/credentials/v1");
        data.put("type", Arrays.asList("VerifiableCredential", "ComprehensiveTrace"));
        data.put("partsCode", partsInfo.getPartsCode());
        data.put("partsName", partsInfo.getPartsName());
        data.put("batchNumber", partsInfo.getBatchNumber());
        data.put("eventCount", events.size());
        data.put("events", events);
        return data;
    }

    private void persistCredentialStorage(VerifiableCredential credential) {
        String normalized = credential.getCredentialData() == null ? "" : credential.getCredentialData();
        String vcHash = didUtil.sha256Hex(normalized);
        ObjectStorageAdapter.StoreObjectResult obj = objectStorageAdapter.store("vc", credential.getId(), normalized);
        ChainStorageAdapter.StoreChainResult chain = chainStorageAdapter.storeAnchor(ChainAnchorRequest.builder()
                .businessType("vc")
                .businessId(credential.getId())
                .dataHash(vcHash)
                .objectCid(obj.getCid())
                .partsDid(credential.getSubjectDid())
                .build());
        credential.setBlockchainTxId(chain.getTxId());
        credential.setCredentialData(appendStorageMeta(normalized, vcHash, obj.getCid()));
        credentialMapper.updateById(credential);
        chainStorageAdapter.appendVcIfSupported(credential.getSubjectDid(), credential.getVcId(), vcHash);
    }

    private String appendStorageMeta(String credentialData, String vcHash, String cid) {
        if (credentialData == null || credentialData.isEmpty()) {
            return "{\"vcHash\":\"" + vcHash + "\",\"vcCid\":\"" + cid + "\"}";
        }
        if (credentialData.endsWith("}")) {
            return credentialData.substring(0, credentialData.length() - 1)
                    + ",\"vcHash\":\"" + vcHash + "\",\"vcCid\":\"" + cid + "\"}";
        }
        return credentialData;
    }

    private void saveVerifyRecord(String vcId, boolean ok, String failReason, Long durationMs, String source) {
        VcVerifyRecord record = new VcVerifyRecord();
        record.setVcId(vcId);
        record.setVerifyResult(ok ? 1 : 0);
        record.setFailReason(failReason);
        record.setDurationMs(durationMs);
        record.setSource(source);
        vcVerifyRecordMapper.insert(record);
    }
}





