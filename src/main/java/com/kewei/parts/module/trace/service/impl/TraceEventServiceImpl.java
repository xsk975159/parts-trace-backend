package com.kewei.parts.module.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.infrastructure.storage.ChainAnchorRequest;
import com.kewei.parts.infrastructure.storage.ChainRouter;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import com.kewei.parts.infrastructure.storage.ObjectStorageAdapter;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.entity.PartsInfo;
import com.kewei.parts.module.parts.entity.PartsUnit;
import com.kewei.parts.module.parts.mapper.PartsDidMapper;
import com.kewei.parts.module.parts.mapper.PartsInfoMapper;
import com.kewei.parts.module.parts.mapper.PartsUnitMapper;
import com.kewei.parts.module.trace.dto.LogisticsEventDTO;
import com.kewei.parts.module.trace.dto.ProductionEventDTO;
import com.kewei.parts.module.trace.dto.QualityEventDTO;
import com.kewei.parts.module.trace.entity.*;
import com.kewei.parts.module.trace.mapper.*;
import com.kewei.parts.module.trace.service.TraceEventService;
import com.kewei.parts.module.trace.vo.TraceEventDetailVO;
import com.kewei.parts.utils.DidUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;

@Service
public class TraceEventServiceImpl implements TraceEventService {

    @Autowired private TraceEventMapper traceEventMapper;
    @Autowired private TraceProductionMapper traceProductionMapper;
    @Autowired private TraceQualityMapper traceQualityMapper;
    @Autowired private TraceLogisticsMapper traceLogisticsMapper;
    @Autowired private VerifiableCredentialMapper verifiableCredentialMapper;
    @Autowired private PartsInfoMapper partsInfoMapper;
    @Autowired private PartsDidMapper partsDidMapper;
    @Autowired private PartsUnitMapper partsUnitMapper;
    @Autowired private ObjectStorageAdapter objectStorageAdapter;
    @Autowired private ChainStorageAdapter chainStorageAdapter;
    @Autowired private ChainRouter chainRouter;
    @Autowired private DidUtil didUtil;
    @Autowired private com.kewei.parts.security.CurrentActor actor;
    @Autowired private com.kewei.parts.module.trace.service.EventSnapshot snapshots;
    @Autowired private com.kewei.parts.module.trace.service.VerifiableCredentialService credentials;
    @Autowired private com.kewei.parts.module.anomaly.service.QualitySafetyService safety;
    @Autowired private javax.validation.Validator validator;
    @Autowired private com.kewei.parts.module.parts.service.DidDocumentVerifier documentVerifier;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent recordProductionEvent(ProductionEventDTO dto) {
        TraceEvent duplicate=prepare(dto,"production");
        if(duplicate!=null) return duplicate;
        if (partsInfoMapper.selectById(dto.getPartsId()) == null) throw new BusinessException("part not found");

        TraceEvent e = buildBase(generateCode("PROD"), dto.getPartsId(), dto.getUnitId(), "production", dto.getEventName(), dto.getEventTime(), dto.getEventLocation(), dto.getOperatorId(), dto.getOperatorName(), dto.getOrganization(), dto.getEvidenceUrls(), dto.getRemark());
        TraceEvent prev = getLastEvent(dto.getPartsId(), dto.getUnitId());
        if (prev != null) e.setPreviousEventId(prev.getId());
        traceEventMapper.insert(e);

        if (prev != null) { prev.setNextEventId(e.getId()); traceEventMapper.updateById(prev); }
        TraceProduction p = new TraceProduction();
        p.setEventId(e.getId()); p.setPartsId(dto.getPartsId());
        p.setProductionLine(dto.getProductionLine()); p.setProductionBatch(dto.getProductionBatch());
        p.setProductionQuantity(dto.getProductionQuantity()); p.setRawMaterials(dto.getRawMaterials());
        p.setProductionProcess(dto.getProductionProcess()); p.setEquipmentInfo(dto.getEquipmentInfo());
        p.setTemperature(dto.getTemperature()); p.setHumidity(dto.getHumidity()); p.setPressure(dto.getPressure());
        p.setQualityLevel(dto.getQualityLevel()); p.setInspector(dto.getInspector()); p.setInspectionResult(dto.getInspectionResult());
        traceProductionMapper.insert(p);
        assignRequest(e,dto);persistEventStorage(e);
        credentials.issueProductionCertificate(e.getId());

        return e;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent recordQualityEvent(QualityEventDTO dto) {
        TraceEvent duplicate=prepare(dto,"quality");
        if(duplicate!=null) return duplicate;
        if (partsInfoMapper.selectById(dto.getPartsId()) == null) throw new BusinessException("part not found");

        TraceEvent e = buildBase(generateCode("QUAL"), dto.getPartsId(), dto.getUnitId(), "quality", dto.getEventName(), dto.getEventTime(), dto.getEventLocation(), dto.getOperatorId(), dto.getOperatorName(), dto.getOrganization(), dto.getEvidenceUrls(), dto.getRemark());
        TraceEvent prev = getLastEvent(dto.getPartsId(), dto.getUnitId());
        if (prev != null) e.setPreviousEventId(prev.getId());
        traceEventMapper.insert(e);

        if (prev != null) { prev.setNextEventId(e.getId()); traceEventMapper.updateById(prev); }
        TraceQuality q = new TraceQuality();
        q.setEventId(e.getId()); q.setPartsId(dto.getPartsId());
        q.setInspectionType(dto.getInspectionType()); q.setInspectionStandard(dto.getInspectionStandard());
        q.setInspectionItems(dto.getInspectionItems()); q.setInspectionResult(dto.getInspectionResult());
        q.setQualifiedQuantity(dto.getQualifiedQuantity()); q.setUnqualifiedQuantity(dto.getUnqualifiedQuantity());
        q.setDefectDescription(dto.getDefectDescription()); q.setInspector(dto.getInspector());
        q.setInspectorId(dto.getInspectorId()); q.setInspectionEquipment(dto.getInspectionEquipment());
        q.setTestData(dto.getTestData()); q.setReportNumber(dto.getReportNumber()); q.setReportUrl(dto.getReportUrl());
        traceQualityMapper.insert(q);
        assignRequest(e,dto);persistEventStorage(e);
        credentials.issueQualityReport(e.getId());
        safety.afterQuality(e,q);
        return e;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent recordLogisticsEvent(LogisticsEventDTO dto) {
        TraceEvent duplicate=prepare(dto,"logistics");
        if(duplicate!=null) return duplicate;
        if (partsInfoMapper.selectById(dto.getPartsId()) == null) throw new BusinessException("part not found");

        TraceEvent e = buildBase(generateCode("LOGI"), dto.getPartsId(), dto.getUnitId(), "logistics", dto.getEventName(), dto.getEventTime(), dto.getEventLocation(), dto.getOperatorId(), dto.getOperatorName(), dto.getOrganization(), dto.getEvidenceUrls(), dto.getRemark());
        TraceEvent prev = getLastEvent(dto.getPartsId(), dto.getUnitId());
        if (prev != null) e.setPreviousEventId(prev.getId());
        traceEventMapper.insert(e);

        if (prev != null) { prev.setNextEventId(e.getId()); traceEventMapper.updateById(prev); }
        TraceLogistics l = new TraceLogistics();
        l.setEventId(e.getId()); l.setPartsId(dto.getPartsId());
        l.setLogisticsType(dto.getLogisticsType()); l.setLogisticsCompany(dto.getLogisticsCompany());
        l.setTrackingNumber(dto.getTrackingNumber()); l.setSender(dto.getSender()); l.setSenderAddress(dto.getSenderAddress());
        l.setReceiver(dto.getReceiver()); l.setReceiverAddress(dto.getReceiverAddress());
        l.setDepartureTime(dto.getDepartureTime()); l.setArrivalTime(dto.getArrivalTime());
        l.setCurrentLocation(dto.getCurrentLocation()); l.setTransportMode(dto.getTransportMode());
        l.setVehicleNumber(dto.getVehicleNumber()); l.setDriver(dto.getDriver()); l.setDriverPhone(dto.getDriverPhone());
        l.setTemperatureRecord(dto.getTemperatureRecord()); l.setHumidityRecord(dto.getHumidityRecord());
        l.setGpsTrack(dto.getGpsTrack()); l.setPackageStatus(dto.getPackageStatus()); l.setSignatureUrl(dto.getSignatureUrl());
        traceLogisticsMapper.insert(l);
        assignRequest(e,dto);persistEventStorage(e);
        credentials.issueTransferCertificate(e.getId());

        return e;
    }

    @Override
    public List<TraceEvent> getPartEvents(Long partsId) {
        LambdaQueryWrapper<TraceEvent> w = new LambdaQueryWrapper<>();
        w.eq(TraceEvent::getPartsId, partsId).orderByAsc(TraceEvent::getEventTime);
        return traceEventMapper.selectList(w);
    }

    @Override
    public List<TraceEvent> getTraceChain(Long partsId) {
        LambdaQueryWrapper<TraceEvent> w = new LambdaQueryWrapper<>();
        w.eq(TraceEvent::getPartsId, partsId).isNull(TraceEvent::getPreviousEventId)
                .orderByAsc(TraceEvent::getUnitId).orderByAsc(TraceEvent::getId);
        List<TraceEvent> heads = traceEventMapper.selectList(w);
        if (heads.isEmpty()) {
            return new ArrayList<>();
        }
        List<TraceEvent> chains = new ArrayList<>();
        for (TraceEvent head : heads) {
            chains.addAll(buildChainFromHead(head));
        }
        return chains;
    }

    private List<TraceEvent> buildChainFromHead(TraceEvent head) {
        List<TraceEvent> chain = new ArrayList<>();
        TraceEvent cur = head;
        Set<Long> visited=new HashSet<>();
        while (cur != null) {
            if(!visited.add(cur.getId())) throw new BusinessException("事件链接形成循环，请核查数据");
            chain.add(cur);
            cur = cur.getNextEventId() != null ? traceEventMapper.selectById(cur.getNextEventId()) : null;
        }
        return chain;
    }

    @Override
    public TraceEventDetailVO getEventDetail(Long eventId) {
        TraceEvent te = traceEventMapper.selectById(eventId);
        if (te == null) throw new BusinessException("event not found");
        TraceEventDetailVO vo = new TraceEventDetailVO();
        BeanUtils.copyProperties(te, vo);
        PartsInfo pi = partsInfoMapper.selectById(te.getPartsId());
        if (pi != null) { vo.setPartsName(pi.getPartsName()); vo.setBatchNumber(pi.getBatchNumber()); }
        vo.setEventTypeName(te.getEventType());
        vo.setStatusName(te.getStatus() == null ? "unknown" : String.valueOf(te.getStatus()));
        Object detail = null;
        if ("production".equals(te.getEventType())) {
            LambdaQueryWrapper<TraceProduction> qw = new LambdaQueryWrapper<>();
            qw.eq(TraceProduction::getEventId, eventId);
            detail = traceProductionMapper.selectOne(qw);
        } else if ("quality".equals(te.getEventType())) {
            LambdaQueryWrapper<TraceQuality> qw = new LambdaQueryWrapper<>();
            qw.eq(TraceQuality::getEventId, eventId);
            detail = traceQualityMapper.selectOne(qw);
        } else if ("logistics".equals(te.getEventType())) {
            LambdaQueryWrapper<TraceLogistics> qw = new LambdaQueryWrapper<>();
            qw.eq(TraceLogistics::getEventId, eventId);
            detail = traceLogisticsMapper.selectOne(qw);
        }
        vo.setDetailInfo(detail);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEvent(Long eventId) {
        actor.requireRoles("admin");
        TraceEvent event=traceEventMapper.lockById(eventId);
        if(event==null) throw new BusinessException("事件不存在");
        for(VerifiableCredential vc:verifiableCredentialMapper.selectList(new LambdaQueryWrapper<VerifiableCredential>().eq(VerifiableCredential::getEventId,eventId)))
            credentials.revokeCredential(vc.getVcId(),"关联事件已作废");
        event.setStatus(0);event.setAuditComment("管理员作废，保留原始内容和事件链接");traceEventMapper.updateById(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent createEvent(TraceEvent event) {
        throw new BusinessException("请通过生产、质检或物流专用接口提交完整事件，通用写入入口已停用");
    }

    @Override
    public List<TraceEvent> getEventList(Long partsId, Long unitId, String partsCode, String eventType) {
        LambdaQueryWrapper<TraceEvent> w = new LambdaQueryWrapper<>();
        if (partsId != null) {
            w.eq(TraceEvent::getPartsId, partsId);
        } else if (StringUtils.hasText(partsCode)) {
            LambdaQueryWrapper<PartsInfo> pw = new LambdaQueryWrapper<>();
            pw.eq(PartsInfo::getPartsCode, partsCode);
            PartsInfo pi = partsInfoMapper.selectOne(pw);
            if (pi != null) w.eq(TraceEvent::getPartsId, pi.getId());
            else return new ArrayList<>();
        }
        if (unitId != null) {
            w.eq(TraceEvent::getUnitId, unitId);
        }
        if (StringUtils.hasText(eventType)) w.eq(TraceEvent::getEventType, eventType);
        w.orderByDesc(TraceEvent::getEventTime);
        return traceEventMapper.selectList(w);
    }

    @Override
    public List<?> getCertificateList(Long partsId, String partsCode) {
        LambdaQueryWrapper<VerifiableCredential> w = new LambdaQueryWrapper<>();
        if (partsId != null) w.eq(VerifiableCredential::getPartsId, partsId);
        if (StringUtils.hasText(partsCode)) {
            LambdaQueryWrapper<PartsInfo> pw = new LambdaQueryWrapper<>();
            pw.eq(PartsInfo::getPartsCode, partsCode);
            PartsInfo pi = partsInfoMapper.selectOne(pw);
            if (pi != null) w.eq(VerifiableCredential::getPartsId, pi.getId());
        }
        w.orderByDesc(VerifiableCredential::getCreateTime);
        return verifiableCredentialMapper.selectList(w);
    }

    @Override
    public Object getCertificateDetail(Long id) {
        VerifiableCredential c = verifiableCredentialMapper.selectById(id);
        if (c == null) throw new BusinessException("cert not found");
        return c;
    }

    @Override
    public Object verifyCertificate(Long id) {
        VerifiableCredential credential=verifiableCredentialMapper.selectById(id);
        if(credential==null) throw new BusinessException("凭证不存在");
        return credentials.verifyCredentialStandard(credential.getVcId());
    }

    @Override
    public Object queryTraceByPartsCode(String partsCode) {
        LambdaQueryWrapper<PartsInfo> pw = new LambdaQueryWrapper<>();
        pw.eq(PartsInfo::getPartsCode, partsCode);
        PartsInfo pi = partsInfoMapper.selectOne(pw);
        Map<String, Object> r = new HashMap<>();
        if (pi == null) { r.put("parts", null); r.put("events", new ArrayList<>()); r.put("certificates", new ArrayList<>()); r.put("chain", new ArrayList<>()); return r; }
        LambdaQueryWrapper<VerifiableCredential> cw = new LambdaQueryWrapper<>();
        cw.eq(VerifiableCredential::getPartsId, pi.getId())
                .orderByDesc(VerifiableCredential::getCreateTime);
        r.put("parts", pi); r.put("events", getPartEvents(pi.getId()));
        r.put("certificates", verifiableCredentialMapper.selectList(cw)); r.put("chain", getTraceChain(pi.getId()));
        return r;
    }

    @Override
    public Object getTraceChainByDid(String did) {
        PartsDid partsDid = getPartsDidEntity(did);
        Map<String, Object> result = new HashMap<>();
        PartsInfo parts = partsInfoMapper.selectById(partsDid.getPartsId());
        LambdaQueryWrapper<TraceEvent> ew = new LambdaQueryWrapper<>();
        applyDidScopeToEvents(ew, partsDid);
        ew.orderByAsc(TraceEvent::getEventTime);
        LambdaQueryWrapper<VerifiableCredential> vw = new LambdaQueryWrapper<>();
        applyDidScopeToCredentials(vw, partsDid);
        vw.orderByDesc(VerifiableCredential::getCreateTime);
        result.put("didInfo", partsDid);
        result.put("partsInfo", parts);
        if (partsDid.getUnitId() != null) {
            result.put("unitInfo", partsUnitMapper.selectById(partsDid.getUnitId()));
        }
        result.put("events", traceEventMapper.selectList(ew));
        result.put("credentials", verifiableCredentialMapper.selectList(vw));
        result.put("verify", verifyTraceChainByDid(did));
        return result;
    }

    @Override
    public Object verifyTraceChainByDid(String did) {
        PartsDid part=getPartsDidEntity(did);List<String> issues=new ArrayList<>();
        if(!documentVerifier.verify(part)) issues.add("DID文档与存证原文或链上摘要不一致，或存证尚未确认");
        if(!Integer.valueOf(1).equals(part.getDidStatus())) issues.add("DID未激活或已冻结");
        if(!Integer.valueOf(1).equals(part.getBindStatus())) issues.add("DID尚未绑定物理标识");
        LambdaQueryWrapper<TraceEvent> ew=new LambdaQueryWrapper<>();applyDidScopeToEvents(ew,part);ew.orderByAsc(TraceEvent::getId);
        List<TraceEvent> events=traceEventMapper.selectList(ew);if(events.isEmpty()) issues.add("缺少事件");
        LambdaQueryWrapper<VerifiableCredential> vw=new LambdaQueryWrapper<>();applyDidScopeToCredentials(vw,part);
        List<VerifiableCredential> vcs=verifiableCredentialMapper.selectList(vw);if(vcs.isEmpty()) issues.add("缺少凭证");
        Set<Long> covered=new HashSet<>();List<Object> details=new ArrayList<>();
        for(VerifiableCredential vc:vcs) {
            Map<?,?> check=(Map<?,?>)credentials.verifyCredentialStandard(vc.getVcId());details.add(check);
            if(!Boolean.TRUE.equals(check.get("verified"))) issues.add("凭证核验失败: "+vc.getVcId());
            else if(vc.getEventId()!=null) covered.add(vc.getEventId());
        }
        for(TraceEvent event:events) {
            if(!covered.contains(event.getId())) issues.add("事件缺少有效凭证: "+event.getEventCode());
            try {
                String raw=snapshots.canonical(event);String hash=didUtil.sha256Hex(raw);
                if(!Integer.valueOf(1).equals(event.getStatus()) || !hash.equals(event.getEventHash())
                    || !hash.equals(chainStorageAdapter.getAnchorHash(chainRouter.eventChain(event.getEventType()),"trace_event",event.getId()))
                    || !raw.equals(objectStorageAdapter.read(event.getAttachmentCid()))) issues.add("事件原文或链上摘要不一致: "+event.getEventCode());
            } catch(Exception e) { issues.add("事件存证尚未确认或无法读取: "+event.getEventCode()); }
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("did",did);result.put("partsId",part.getPartsId());result.put("unitId",part.getUnitId());
        result.put("passed",issues.isEmpty());result.put("issues",issues);result.put("eventCount",events.size());result.put("vcCount",vcs.size());
        result.put("credentialChecks",details);result.put("simulated",chainStorageAdapter.isSimulated());return result;
    }

    @Override
    public Object exportTraceReport(String did, String format) {
        Object chain = getTraceChainByDid(did);
        if ("csv".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) chain;
            PartsDid didInfo = (PartsDid) map.get("didInfo");
            PartsInfo parts = (PartsInfo) map.get("partsInfo");
            @SuppressWarnings("unchecked")
            List<TraceEvent> events = (List<TraceEvent>) map.get("events");
            StringBuilder sb = new StringBuilder();
            sb.append("did,partsCode,partsName,batchNumber,eventCode,eventType,eventTime,organization,txId\n");
            for (TraceEvent e : events) {
                sb.append(safe(didInfo == null ? null : didInfo.getDid())).append(",")
                        .append(safe(parts == null ? null : parts.getPartsCode())).append(",")
                        .append(safe(parts == null ? null : parts.getPartsName())).append(",")
                        .append(safe(parts == null ? null : parts.getBatchNumber())).append(",")
                        .append(safe(e.getEventCode())).append(",")
                        .append(safe(e.getEventType())).append(",")
                        .append(safe(String.valueOf(e.getEventTime()))).append(",")
                        .append(safe(e.getOrganization())).append(",")
                        .append(safe(e.getBlockchainTxId())).append("\n");
            }
            return sb.toString();
        }
        return chain;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent markEventAbnormal(Long eventId, String reason) {
        actor.requireRoles("quality");
        TraceEvent event = traceEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("事件不存在");
        }
        event.setAbnormalMark(1);
        event.setAbnormalReason(reason);
        event.setAuditStatus(1);
        traceEventMapper.updateById(event);
        return event;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent auditEvent(Long eventId, Integer auditStatus, String auditComment) {
        actor.requireRoles("quality");
        if(auditStatus==null || auditStatus<0 || auditStatus>2) throw new BusinessException("审核状态不合法");
        TraceEvent event = traceEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("事件不存在");
        }
        event.setAuditStatus(auditStatus);
        if (StringUtils.hasText(auditComment)) {
            event.setAuditComment(auditComment);
        }
        traceEventMapper.updateById(event);
        return event;
    }

    private PartsDid getPartsDidEntity(String did) {
        LambdaQueryWrapper<PartsDid> dw = new LambdaQueryWrapper<>();
        dw.eq(PartsDid::getDid, did).last("LIMIT 1");
        PartsDid partsDid = partsDidMapper.selectOne(dw);
        if (partsDid == null) {
            throw new BusinessException("DID不存在");
        }
        return partsDid;
    }

    /** 按 DID 对应单件缩小事件查询范围，避免同 parts_id 下其他单件事件混入 */
    private void applyDidScopeToEvents(LambdaQueryWrapper<TraceEvent> w, PartsDid partsDid) {
        if (partsDid.getUnitId() != null) {
            w.eq(TraceEvent::getUnitId, partsDid.getUnitId());
            return;
        }
        if (StringUtils.hasText(partsDid.getDid())) {
            w.eq(TraceEvent::getPartsDid, partsDid.getDid());
            return;
        }
        w.eq(TraceEvent::getPartsId, partsDid.getPartsId());
    }

    /** 按 DID 对应单件缩小 VC 查询范围 */
    private void applyDidScopeToCredentials(LambdaQueryWrapper<VerifiableCredential> w, PartsDid partsDid) {
        if (partsDid.getUnitId() != null) {
            w.eq(VerifiableCredential::getUnitId, partsDid.getUnitId());
            return;
        }
        if (StringUtils.hasText(partsDid.getDid())) {
            w.eq(VerifiableCredential::getSubjectDid, partsDid.getDid());
            return;
        }
        w.eq(VerifiableCredential::getPartsId, partsDid.getPartsId());
    }

    private void ensurePartsDidActivated(Long partsId, Long unitId) {
        PartsDid partsDid = resolvePartsDid(partsId, unitId);
        if (partsDid == null) {
            throw new BusinessException("零部件未生成DID");
        }
        if (partsDid.getDidStatus() == null || partsDid.getDidStatus() != 1) {
            throw new BusinessException("DID未激活，禁止录入事件");
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        if(!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0))>=0) value="'"+value;
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private void persistEventStorage(TraceEvent event) {
        String raw=snapshots.canonical(event);String hash=didUtil.sha256Hex(raw);
        ObjectStorageAdapter.StoreObjectResult object=objectStorageAdapter.store("trace_event",event.getId(),raw);
        ChainStorageAdapter.StoreChainResult chain=chainStorageAdapter.storeAnchor(ChainAnchorRequest.builder().businessType("trace_event").businessId(event.getId())
            .dataHash(hash).objectCid(object.getCid()).partsDid(event.getPartsDid()).chainId(chainRouter.eventChain(event.getEventType())).build());
        event.setEventHash(hash);event.setAttachmentCid(object.getCid());
        Map<String,Object> meta=new TreeMap<>();meta.put("schemaVersion",2);meta.put("eventHash",hash);meta.put("contentCid",object.getCid());
        event.setEventData(com.kewei.parts.utils.CanonicalJson.write(meta));event.setBlockchainTxId(chain.getTxId());event.setChainStatus(chain.isSimulated()?2:chain.getTxId()==null?0:1);
        traceEventMapper.updateById(event);
    }

    private TraceEvent buildBase(String code, Long partsId, Long unitId, String type, String name,
            java.util.Date time, String loc, Long opId, String opName, String org, String evidenceUrls, String remark) {
        TraceEvent e = new TraceEvent();
        e.setEventCode(code); e.setPartsId(partsId); e.setUnitId(unitId); e.setEventType(type); e.setEventName(name);
        e.setEventTime(new Date(time.getTime()/1000*1000)); e.setEventLocation(loc); e.setOperatorId(opId); e.setOperatorName(opName);
        e.setOrganization(org); e.setEvidenceUrls(evidenceUrls); e.setStatus(1); e.setRemark(remark);
        e.setChainStatus(0); e.setRetryCount(0); e.setAbnormalMark(0); e.setAuditStatus(0);
        PartsInfo p = partsInfoMapper.selectById(partsId);
        if (p != null) {
            e.setBatchNumber(p.getBatchNumber());
        }
        PartsDid pd = resolvePartsDid(partsId, unitId);
        if (pd != null) {
            e.setPartsDid(pd.getDid());
        }
        return e;
    }

    private PartsDid resolvePartsDid(Long partsId, Long unitId) {
        LambdaQueryWrapper<PartsDid> dw = new LambdaQueryWrapper<>();
        dw.eq(PartsDid::getPartsId, partsId);
        if (unitId != null) {
            dw.eq(PartsDid::getUnitId, unitId);
        } else {
            dw.eq(PartsDid::getPartsId, partsId);
        }
        dw.orderByDesc(PartsDid::getId).last("LIMIT 1");
        return partsDidMapper.selectOne(dw);
    }

    private TraceEvent getLastEvent(Long partsId, Long unitId) {
        LambdaQueryWrapper<TraceEvent> w = new LambdaQueryWrapper<>();
        w.eq(TraceEvent::getPartsId, partsId);
        if (unitId != null) w.eq(TraceEvent::getUnitId, unitId);
        else w.isNull(TraceEvent::getUnitId);
        w.isNull(TraceEvent::getNextEventId).orderByDesc(TraceEvent::getId).last("LIMIT 1");
        return traceEventMapper.selectOne(w);
    }

    private String generateCode(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private TraceEvent prepare(Object dto,String type) {
        actor.requireRoles("production".equals(type)?"manufacturer":"quality".equals(type)?"quality":"logistics");
        org.springframework.beans.BeanWrapper fields=new org.springframework.beans.BeanWrapperImpl(dto);
        Long partsId=(Long)fields.getPropertyValue("partsId");
        PartsInfo part=partsInfoMapper.lockById(partsId);
        if(part==null) throw new BusinessException("零部件不存在");
        if("production".equals(type)) actor.requireSelfOrAdmin(part.getCreatorId());
        Long unitId=(Long)fields.getPropertyValue("unitId");
        if(unitId==null) {
            List<PartsUnit> units=partsUnitMapper.selectList(new LambdaQueryWrapper<PartsUnit>().eq(PartsUnit::getPartsId,partsId));
            if(units.size()!=1) throw new BusinessException("请明确选择单件，不能把多个单件合并记账");
            unitId=units.get(0).getId();fields.setPropertyValue("unitId",unitId);
        }
        PartsUnit unit=partsUnitMapper.selectById(unitId);
        if(unit==null || !Objects.equals(unit.getPartsId(),partsId)) throw new BusinessException("单件与零部件不匹配");
        com.kewei.parts.module.system.entity.SysUser user=actor.user();
        fields.setPropertyValue("operatorId",user.getId());fields.setPropertyValue("operatorName",user.getRealName()==null?user.getUsername():user.getRealName());
        fields.setPropertyValue("organization",user.getOrganization()==null?"未登记机构":user.getOrganization());
        if(fields.isWritableProperty("inspector")) fields.setPropertyValue("inspector",fields.getPropertyValue("operatorName"));
        if(fields.isWritableProperty("inspectorId")) fields.setPropertyValue("inspectorId",user.getId());
        Date time=(Date)fields.getPropertyValue("eventTime");
        if(time==null || time.getTime()>System.currentTimeMillis()+300000) throw new BusinessException("事件时间缺失或超出允许范围");
        fields.setPropertyValue("eventTime",new Date(time.getTime()/1000*1000));
        java.util.Set<javax.validation.ConstraintViolation<Object>> violations=validator.validate(dto);
        if(!violations.isEmpty()) throw new BusinessException(violations.iterator().next().getMessage());
        snapshots.evidence((String)fields.getPropertyValue("evidenceUrls"));
        String requestId=requestKey(dto);
        TraceEvent old=traceEventMapper.selectOne(new LambdaQueryWrapper<TraceEvent>().eq(TraceEvent::getRequestId,requestId));
        if(old!=null) {
            if(!Objects.equals(old.getRequestHash(),didUtil.sha256Hex(com.kewei.parts.utils.CanonicalJson.write(dto)))) throw new BusinessException("同一提交编号不能使用不同业务内容");
            return old;
        }
        PartsDid did=resolvePartsDid(partsId,unitId);
        if(did==null || !Integer.valueOf(1).equals(did.getBindStatus()) || (!Integer.valueOf(1).equals(did.getDidStatus())
                && !("quality".equals(type) && Integer.valueOf(2).equals(did.getDidStatus())))) throw new BusinessException("DID尚未绑定、未激活或已冻结");
        TraceEvent previous=getLastEvent(partsId,unitId);
        if("production".equals(type) && previous!=null) throw new BusinessException("该单件已经登记生产事件");
        if("quality".equals(type) && (previous==null || !("production".equals(previous.getEventType()) || ("quality".equals(previous.getEventType()) && Integer.valueOf(2).equals(did.getDidStatus())))))
            throw new BusinessException("质检须在生产之后进行；冻结单件可追加复检");
        if("quality".equals(type) && !com.kewei.parts.module.anomaly.service.QualitySafetyService.knownResult((String)fields.getPropertyValue("inspectionResult")))
            throw new BusinessException("请明确选择合格或不合格，缺省结果不能视为合格");
        if("logistics".equals(type)) {
            TraceQuality quality=previous==null?null:traceQualityMapper.selectOne(new LambdaQueryWrapper<TraceQuality>().eq(TraceQuality::getEventId,previous.getId()));
            if(previous==null || !"quality".equals(previous.getEventType()) || quality==null || !com.kewei.parts.module.anomaly.service.QualitySafetyService.passed(quality.getInspectionResult()))
                throw new BusinessException("只有通过质检的单件才能进入物流");
        }
        return null;
    }
    private String requestKey(Object dto) {
        org.springframework.beans.BeanWrapper fields=new org.springframework.beans.BeanWrapperImpl(dto);
        String id=(String)fields.getPropertyValue("requestId");
        if(id!=null && id.length()>128) throw new BusinessException("提交编号过长");
        String scope=actor.id()+"|"+fields.getPropertyValue("partsId")+"|"+fields.getPropertyValue("unitId")+"|"+dto.getClass().getSimpleName();
        return didUtil.sha256Hex(scope+"|"+(id==null?com.kewei.parts.utils.CanonicalJson.write(dto):id));
    }
    private void assignRequest(TraceEvent event,Object dto) {
        event.setRequestId(requestKey(dto));event.setRequestHash(didUtil.sha256Hex(com.kewei.parts.utils.CanonicalJson.write(dto)));
    }

    @Override public Map<String,Object> getEventPage(int page,int size,Long partsId,Long unitId,String code,String type) {
        validatePage(page,size);LambdaQueryWrapper<TraceEvent> w=new LambdaQueryWrapper<>();
        Long resolved=resolvePartsFilter(partsId,code);
        w.eq(resolved!=null,TraceEvent::getPartsId,resolved).eq(unitId!=null,TraceEvent::getUnitId,unitId).eq(type!=null && !type.isEmpty(),TraceEvent::getEventType,type).orderByDesc(TraceEvent::getEventTime);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TraceEvent> result=traceEventMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page,size),w);
        return pageResult(result.getRecords(),result.getTotal());
    }
    @Override public Map<String,Object> getCertificatePage(int page,int size,Long partsId,String code) {
        validatePage(page,size);LambdaQueryWrapper<VerifiableCredential> w=new LambdaQueryWrapper<>();Long resolved=resolvePartsFilter(partsId,code);
        w.eq(resolved!=null,VerifiableCredential::getPartsId,resolved).orderByDesc(VerifiableCredential::getCreateTime);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VerifiableCredential> result=verifiableCredentialMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page,size),w);
        return pageResult(result.getRecords(),result.getTotal());
    }
    private Long resolvePartsFilter(Long id,String code) {
        if(code==null || code.trim().isEmpty()) return id;
        PartsInfo part=partsInfoMapper.selectOne(new LambdaQueryWrapper<PartsInfo>().eq(PartsInfo::getPartsCode,code));
        return part==null || (id!=null && !id.equals(part.getId()))?-1L:part.getId();
    }
    private void validatePage(int page,int size) { if(page<1 || size<1 || size>500) throw new BusinessException("分页参数须为正数，每页最多500条"); }
    private Map<String,Object> pageResult(List<?> records,long total) { Map<String,Object> result=new HashMap<>();result.put("list",records);result.put("total",total);return result; }
}
