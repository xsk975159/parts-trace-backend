package com.kewei.parts.module.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.infrastructure.storage.ChainStorageAdapter;
import com.kewei.parts.infrastructure.storage.ObjectStorageAdapter;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.entity.PartsInfo;
import com.kewei.parts.module.parts.mapper.PartsDidMapper;
import com.kewei.parts.module.parts.mapper.PartsInfoMapper;
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
    @Autowired private ObjectStorageAdapter objectStorageAdapter;
    @Autowired private ChainStorageAdapter chainStorageAdapter;
    @Autowired private DidUtil didUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent recordProductionEvent(ProductionEventDTO dto) {
        if (partsInfoMapper.selectById(dto.getPartsId()) == null) throw new BusinessException("part not found");
        ensurePartsDidActivated(dto.getPartsId());
        TraceEvent e = buildBase(generateCode("PROD"), dto.getPartsId(), "production", dto.getEventName(), dto.getEventTime(), dto.getEventLocation(), dto.getOperatorId(), dto.getOperatorName(), dto.getOrganization(), dto.getEvidenceUrls(), dto.getRemark());
        TraceEvent prev = getLastEvent(dto.getPartsId());
        if (prev != null) e.setPreviousEventId(prev.getId());
        traceEventMapper.insert(e);
        persistEventStorage(e);
        if (prev != null) { prev.setNextEventId(e.getId()); traceEventMapper.updateById(prev); }
        TraceProduction p = new TraceProduction();
        p.setEventId(e.getId()); p.setPartsId(dto.getPartsId());
        p.setProductionLine(dto.getProductionLine()); p.setProductionBatch(dto.getProductionBatch());
        p.setProductionQuantity(dto.getProductionQuantity()); p.setRawMaterials(dto.getRawMaterials());
        p.setProductionProcess(dto.getProductionProcess()); p.setEquipmentInfo(dto.getEquipmentInfo());
        p.setTemperature(dto.getTemperature()); p.setHumidity(dto.getHumidity()); p.setPressure(dto.getPressure());
        p.setQualityLevel(dto.getQualityLevel()); p.setInspector(dto.getInspector()); p.setInspectionResult(dto.getInspectionResult());
        traceProductionMapper.insert(p);
        return e;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent recordQualityEvent(QualityEventDTO dto) {
        if (partsInfoMapper.selectById(dto.getPartsId()) == null) throw new BusinessException("part not found");
        ensurePartsDidActivated(dto.getPartsId());
        TraceEvent e = buildBase(generateCode("QUAL"), dto.getPartsId(), "quality", dto.getEventName(), dto.getEventTime(), dto.getEventLocation(), dto.getOperatorId(), dto.getOperatorName(), dto.getOrganization(), dto.getEvidenceUrls(), dto.getRemark());
        TraceEvent prev = getLastEvent(dto.getPartsId());
        if (prev != null) e.setPreviousEventId(prev.getId());
        traceEventMapper.insert(e);
        persistEventStorage(e);
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
        return e;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent recordLogisticsEvent(LogisticsEventDTO dto) {
        if (partsInfoMapper.selectById(dto.getPartsId()) == null) throw new BusinessException("part not found");
        ensurePartsDidActivated(dto.getPartsId());
        TraceEvent e = buildBase(generateCode("LOGI"), dto.getPartsId(), "logistics", dto.getEventName(), dto.getEventTime(), dto.getEventLocation(), dto.getOperatorId(), dto.getOperatorName(), dto.getOrganization(), dto.getEvidenceUrls(), dto.getRemark());
        TraceEvent prev = getLastEvent(dto.getPartsId());
        if (prev != null) e.setPreviousEventId(prev.getId());
        traceEventMapper.insert(e);
        persistEventStorage(e);
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
        w.eq(TraceEvent::getPartsId, partsId).isNull(TraceEvent::getPreviousEventId);
        TraceEvent first = traceEventMapper.selectOne(w);
        if (first == null) return new ArrayList<>();
        List<TraceEvent> chain = new ArrayList<>();
        TraceEvent cur = first;
        while (cur != null) {
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
        TraceEvent e = traceEventMapper.selectById(eventId);
        if (e == null) throw new BusinessException("event not found");
        if (e.getPreviousEventId() != null && e.getNextEventId() != null) {
            TraceEvent prev = traceEventMapper.selectById(e.getPreviousEventId());
            if (prev != null) { prev.setNextEventId(e.getNextEventId()); traceEventMapper.updateById(prev); }
            TraceEvent next = traceEventMapper.selectById(e.getNextEventId());
            if (next != null) { next.setPreviousEventId(e.getPreviousEventId()); traceEventMapper.updateById(next); }
        }
        traceEventMapper.deleteById(eventId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TraceEvent createEvent(TraceEvent event) {
        if (event.getStatus() == null) event.setStatus(1);
        if (!StringUtils.hasText(event.getEventCode())) event.setEventCode(generateCode("EVT"));
        traceEventMapper.insert(event);
        return event;
    }

    @Override
    public List<TraceEvent> getEventList(Long partsId, String partsCode, String eventType) {
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
        VerifiableCredential c = verifiableCredentialMapper.selectById(id);
        if (c == null) throw new BusinessException("cert not found");
        boolean valid = c.getStatus() != null && c.getStatus() == 1;
        Map<String, Object> r = new HashMap<>();
        r.put("verified", valid); r.put("certificate", c); r.put("message", valid ? "ok" : "invalid");
        return r;
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
        ew.eq(TraceEvent::getPartsId, partsDid.getPartsId()).orderByAsc(TraceEvent::getEventTime);
        LambdaQueryWrapper<VerifiableCredential> vw = new LambdaQueryWrapper<>();
        vw.eq(VerifiableCredential::getPartsId, partsDid.getPartsId()).orderByDesc(VerifiableCredential::getCreateTime);
        result.put("didInfo", partsDid);
        result.put("partsInfo", parts);
        result.put("events", traceEventMapper.selectList(ew));
        result.put("credentials", verifiableCredentialMapper.selectList(vw));
        result.put("verify", verifyTraceChainByDid(did));
        return result;
    }

    @Override
    public Object verifyTraceChainByDid(String did) {
        PartsDid partsDid = getPartsDidEntity(did);
        List<String> issues = new ArrayList<>();
        if (partsDid.getDidStatus() == null || partsDid.getDidStatus() != 1) {
            issues.add("DID未激活");
        }
        LambdaQueryWrapper<TraceEvent> ew = new LambdaQueryWrapper<>();
        ew.eq(TraceEvent::getPartsId, partsDid.getPartsId());
        List<TraceEvent> events = traceEventMapper.selectList(ew);
        if (events.isEmpty()) {
            issues.add("缺少事件");
        }
        LambdaQueryWrapper<VerifiableCredential> vw = new LambdaQueryWrapper<>();
        vw.eq(VerifiableCredential::getPartsId, partsDid.getPartsId());
        List<VerifiableCredential> vcs = verifiableCredentialMapper.selectList(vw);
        if (vcs.isEmpty()) {
            issues.add("缺少VC");
        }
        Map<String, Object> verify = new HashMap<>();
        verify.put("did", did);
        verify.put("partsId", partsDid.getPartsId());
        verify.put("passed", issues.isEmpty());
        verify.put("issues", issues);
        verify.put("eventCount", events.size());
        verify.put("vcCount", vcs.size());
        return verify;
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
        TraceEvent event = traceEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("事件不存在");
        }
        event.setAuditStatus(auditStatus);
        if (StringUtils.hasText(auditComment)) {
            event.setRemark((event.getRemark() == null ? "" : event.getRemark() + " | ") + "审核:" + auditComment);
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

    private void ensurePartsDidActivated(Long partsId) {
        LambdaQueryWrapper<PartsDid> didWrapper = new LambdaQueryWrapper<>();
        didWrapper.eq(PartsDid::getPartsId, partsId).last("LIMIT 1");
        PartsDid partsDid = partsDidMapper.selectOne(didWrapper);
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
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private void persistEventStorage(TraceEvent event) {
        String attachmentHash = didUtil.sha256Hex(event.getEvidenceUrls() == null ? "" : event.getEvidenceUrls());
        String eventRaw = String.valueOf(event.getPartsId()) + "|" + event.getEventType() + "|" + event.getEventTime() + "|" + attachmentHash;
        String eventHash = didUtil.sha256Hex(eventRaw);
        ObjectStorageAdapter.StoreObjectResult obj = objectStorageAdapter.store("trace_event", event.getId(), eventRaw);
        ChainStorageAdapter.StoreChainResult chain = chainStorageAdapter.storeHash("trace_event", event.getId(), eventHash);
        event.setEventHash(eventHash);
        event.setAttachmentCid(obj.getCid());
        event.setEventData("{\"eventHash\":\"" + eventHash + "\",\"attachmentHash\":\"" + attachmentHash + "\",\"attachmentCid\":\"" + obj.getCid() + "\"}");
        event.setBlockchainTxId(chain.getTxId());
        event.setChainStatus(1);
        traceEventMapper.updateById(event);
    }

    private TraceEvent buildBase(String code, Long partsId, String type, String name,
            java.util.Date time, String loc, Long opId, String opName, String org, String evidenceUrls, String remark) {
        TraceEvent e = new TraceEvent();
        e.setEventCode(code); e.setPartsId(partsId); e.setEventType(type); e.setEventName(name);
        e.setEventTime(time); e.setEventLocation(loc); e.setOperatorId(opId); e.setOperatorName(opName);
        e.setOrganization(org); e.setEvidenceUrls(evidenceUrls); e.setStatus(1); e.setRemark(remark);
        e.setChainStatus(0); e.setRetryCount(0); e.setAbnormalMark(0); e.setAuditStatus(0);
        PartsInfo p = partsInfoMapper.selectById(partsId);
        if (p != null) {
            e.setBatchNumber(p.getBatchNumber());
        }
        LambdaQueryWrapper<PartsDid> dw = new LambdaQueryWrapper<>();
        dw.eq(PartsDid::getPartsId, partsId).last("LIMIT 1");
        PartsDid pd = partsDidMapper.selectOne(dw);
        if (pd != null) {
            e.setPartsDid(pd.getDid());
        }
        return e;
    }

    private TraceEvent getLastEvent(Long partsId) {
        LambdaQueryWrapper<TraceEvent> w = new LambdaQueryWrapper<>();
        w.eq(TraceEvent::getPartsId, partsId).isNull(TraceEvent::getNextEventId).orderByDesc(TraceEvent::getEventTime).last("LIMIT 1");
        return traceEventMapper.selectOne(w);
    }

    private String generateCode(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}





