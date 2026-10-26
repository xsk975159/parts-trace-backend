package com.kewei.parts.module.trace.service;

import com.kewei.parts.module.trace.dto.LogisticsEventDTO;
import com.kewei.parts.module.trace.dto.ProductionEventDTO;
import com.kewei.parts.module.trace.dto.QualityEventDTO;
import com.kewei.parts.module.trace.entity.TraceEvent;
import com.kewei.parts.module.trace.vo.TraceEventDetailVO;

import java.util.List;

/**
 * 溯源事件服务接口
 */
public interface TraceEventService {

    TraceEvent recordProductionEvent(ProductionEventDTO eventDTO);

    TraceEvent recordQualityEvent(QualityEventDTO eventDTO);

    TraceEvent recordLogisticsEvent(LogisticsEventDTO eventDTO);

    List<TraceEvent> getPartEvents(Long partsId);

    List<TraceEvent> getTraceChain(Long partsId);

    TraceEventDetailVO getEventDetail(Long eventId);

    void deleteEvent(Long eventId);

    /** 前端兼容：通用事件创建 */
    TraceEvent createEvent(TraceEvent event);

    /** 前端兼容：事件列表（支持多条件查询） */
    List<TraceEvent> getEventList(Long partsId, Long unitId, String partsCode, String eventType);

    /** 前端兼容：凭证列"*/
    List<?> getCertificateList(Long partsId, String partsCode);

    /** 前端兼容：凭证详"*/
    Object getCertificateDetail(Long id);

    /** 前端兼容：验证凭"*/
    Object verifyCertificate(Long id);

    /** 前端兼容：根据零部件编码查询溯源"*/
    Object queryTraceByPartsCode(String partsCode);

    /** 文档版：按DID聚合查询链条 */
    Object getTraceChainByDid(String did);

    /** 文档版：按DID校验链条完整性 */
    Object verifyTraceChainByDid(String did);

    /** 文档版：导出溯源报告（JSON/CSV） */
    Object exportTraceReport(String did, String format);

    /** 异常事件标记 */
    TraceEvent markEventAbnormal(Long eventId, String reason);

    /** 异常事件审核 */
    TraceEvent auditEvent(Long eventId, Integer auditStatus, String auditComment);
    java.util.Map<String,Object> getEventPage(int page,int pageSize,Long partsId,Long unitId,String partsCode,String eventType);
    java.util.Map<String,Object> getCertificatePage(int page,int pageSize,Long partsId,String partsCode);
}





