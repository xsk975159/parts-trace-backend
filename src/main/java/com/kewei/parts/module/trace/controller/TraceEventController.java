package com.kewei.parts.module.trace.controller;

import com.kewei.parts.common.BusinessException;
import com.kewei.parts.common.Result;
import com.kewei.parts.module.system.service.RoleService;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.entity.PartsInfo;
import com.kewei.parts.module.parts.entity.PartsUnit;
import com.kewei.parts.module.parts.mapper.PartsDidMapper;
import com.kewei.parts.module.parts.mapper.PartsInfoMapper;
import com.kewei.parts.module.parts.mapper.PartsUnitMapper;
import com.kewei.parts.module.trace.dto.LogisticsEventDTO;
import com.kewei.parts.module.trace.dto.ProductionEventDTO;
import com.kewei.parts.module.trace.dto.QualityEventDTO;
import com.kewei.parts.module.trace.dto.WorkbenchBatchActionDTO;
import com.kewei.parts.module.trace.dto.WorkbenchRecordDTO;
import com.kewei.parts.module.trace.entity.TraceEvent;
import com.kewei.parts.module.trace.entity.VerifiableCredential;
import com.kewei.parts.module.trace.mapper.TraceEventMapper;
import com.kewei.parts.module.trace.mapper.VerifiableCredentialMapper;
import com.kewei.parts.module.trace.service.TraceEventService;
import com.kewei.parts.module.trace.service.VerifiableCredentialService;
import com.kewei.parts.module.trace.vo.TraceEventDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 溯源事件控制"
 */
@Tag(name = "溯源事件管理", description = "零部件溯源事件记录接")
@RestController
@RequestMapping("/api/trace")
public class TraceEventController {

    @Autowired
    private TraceEventService traceEventService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private VerifiableCredentialService credentialService;

    @Autowired
    private PartsInfoMapper partsInfoMapper;

    @Autowired
    private PartsDidMapper partsDidMapper;

    @Autowired
    private TraceEventMapper traceEventMapper;

    @Autowired
    private VerifiableCredentialMapper verifiableCredentialMapper;

    @Autowired
    private PartsUnitMapper partsUnitMapper;

    // ==================== 前端 API 兼容接口 ====================

    /**
     * 获取事件列表（前"GET /api/trace/event/list"
     */
    @Operation(summary = "获取事件列表")
    @GetMapping("/event/list")
    public Result<Map<String, Object>> getEventList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long partsId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) String partsCode,
            @RequestParam(required = false) String eventType) {
        List<TraceEvent> events = traceEventService.getEventList(partsId, unitId, partsCode, eventType);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("list", events);
        result.put("total", events.size());
        return Result.success(result);
    }

    /**
     * 创建通用事件（前"POST /api/trace/event/create"
     */
    @Operation(summary = "创建溯源事件")
    @PostMapping("/event/create")
    public Result<TraceEvent> createEvent(@RequestBody TraceEvent event) {
        TraceEvent saved = traceEventService.createEvent(event);
        return Result.success("事件创建成功", saved);
    }

    /**
     * 获取凭证列表（前"GET /api/trace/certificate/list"
     * 转发"VerifiableCredentialController 逻辑
     */
    @Operation(summary = "获取凭证列表")
    @GetMapping("/certificate/list")
    public Result<Map<String, Object>> getCertificateList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long partsId,
            @RequestParam(required = false) String partsCode) {
        List<?> certs = traceEventService.getCertificateList(partsId, partsCode);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("list", certs);
        result.put("total", certs.size());
        return Result.success(result);
    }

    /**
     * 获取凭证详情（前"GET /api/trace/certificate/detail/{id}"
     */
    @Operation(summary = "获取凭证详情")
    @GetMapping("/certificate/detail/{id}")
    public Result<Object> getCertificateDetail(@PathVariable Long id) {
        Object cert = traceEventService.getCertificateDetail(id);
        return Result.success(cert);
    }

    /**
     * 验证凭证（前"POST /api/trace/certificate/verify/{id}"
     */
    @Operation(summary = "验证凭证")
    @PostMapping("/certificate/verify/{id}")
    public Result<Object> verifyCertificate(@PathVariable Long id) {
        Object result = traceEventService.verifyCertificate(id);
        return Result.success(result);
    }

    /**
     * 根据零部件编码查询溯源链（前"GET /api/trace/query/{partsCode}"
     */
    @Operation(summary = "根据零部件编码查询溯源链")
    @GetMapping("/query/{partsCode}")
    public Result<Object> queryTraceByPartsCode(@PathVariable String partsCode) {
        Object result = traceEventService.queryTraceByPartsCode(partsCode);
        return Result.success(result);
    }

    @Operation(summary = "根据DID聚合查询链条")
    @GetMapping("/chain/{did}")
    public Result<Object> getTraceChainByDid(@PathVariable String did) {
        return Result.success(traceEventService.getTraceChainByDid(did));
    }

    @Operation(summary = "根据DID校验链条")
    @GetMapping("/chain/{did}/verify")
    public Result<Object> verifyTraceChainByDid(@PathVariable String did) {
        return Result.success(traceEventService.verifyTraceChainByDid(did));
    }

    @Operation(summary = "导出溯源报告（JSON/CSV）")
    @GetMapping("/report")
    public Object exportTraceReport(@RequestParam String did, @RequestParam(defaultValue = "json") String format) {
        Object report = traceEventService.exportTraceReport(did, format);
        if ("csv".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format)) {
            byte[] bytes = String.valueOf(report).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trace-report.csv")
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(bytes);
        }
        return Result.success(report);
    }

    @Operation(summary = "工作台组合提交（事件+可选VC）")
    @PostMapping("/workbench/record")
    public Result<Map<String, Object>> submitWorkbenchRecord(
            HttpServletRequest request,
            @Validated @RequestBody WorkbenchRecordDTO dto) {
        TraceEvent event;
        String eventType = dto.getEventType() == null ? "" : dto.getEventType().toLowerCase();
        switch (eventType) {
            case "production":
                requireAnyRole(request, "admin", "manufacturer");
                ProductionEventDTO p = new ProductionEventDTO();
                p.setPartsId(dto.getPartsId());
                p.setEventName(dto.getEventName());
                p.setEventTime(dto.getEventTime());
                p.setEventLocation(dto.getEventLocation());
                p.setOperatorId(dto.getOperatorId());
                p.setOperatorName(dto.getOperatorName());
                p.setOrganization(dto.getOrganization());
                p.setEvidenceUrls(dto.getEvidenceUrls());
                p.setProductionLine(dto.getProductionLine());
                p.setProductionBatch(dto.getProductionBatch());
                event = traceEventService.recordProductionEvent(p);
                break;
            case "quality":
                requireAnyRole(request, "admin", "quality");
                QualityEventDTO q = new QualityEventDTO();
                q.setPartsId(dto.getPartsId());
                q.setEventName(dto.getEventName());
                q.setEventTime(dto.getEventTime());
                q.setEventLocation(dto.getEventLocation());
                q.setOperatorId(dto.getOperatorId());
                q.setOperatorName(dto.getOperatorName());
                q.setOrganization(dto.getOrganization());
                q.setEvidenceUrls(dto.getEvidenceUrls());
                q.setInspectionType(dto.getInspectionType() == null ? "常规检验" : dto.getInspectionType());
                q.setInspectionResult(dto.getInspectionResult() == null ? "合格" : dto.getInspectionResult());
                q.setInspector(dto.getOperatorName());
                event = traceEventService.recordQualityEvent(q);
                break;
            case "logistics":
                requireAnyRole(request, "admin", "logistics");
                LogisticsEventDTO l = new LogisticsEventDTO();
                l.setPartsId(dto.getPartsId());
                l.setEventName(dto.getEventName());
                l.setEventTime(dto.getEventTime());
                l.setEventLocation(dto.getEventLocation());
                l.setOperatorId(dto.getOperatorId());
                l.setOperatorName(dto.getOperatorName());
                l.setOrganization(dto.getOrganization());
                l.setEvidenceUrls(dto.getEvidenceUrls());
                l.setLogisticsType(dto.getLogisticsType() == null ? "运输" : dto.getLogisticsType());
                l.setTrackingNumber(dto.getTrackingNumber());
                event = traceEventService.recordLogisticsEvent(l);
                break;
            default:
                throw new BusinessException("不支持的事件类型");
        }

        VerifiableCredential vc = issueVcForEvent(event, eventType);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("event", event);
        result.put("credential", vc);
        result.put("chainUpdated", true);
        return Result.success("工作台提交成功", result);
    }

    @Operation(summary = "工作台待办任务")
    @GetMapping("/workbench/tasks")
    public Result<List<Map<String, Object>>> getWorkbenchTasks(@RequestParam(defaultValue = "20") Integer limit) {
        List<TraceEvent> events = traceEventService.getEventList(null, null, null, null);
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (TraceEvent e : events) {
            Map<String, Object> t = new java.util.HashMap<>();
            t.put("id", e.getId());
            t.put("type", e.getAbnormalMark() != null && e.getAbnormalMark() == 1 ? "异常审核" : "事件复核");
            t.put("title", (e.getEventName() == null ? "-" : e.getEventName()) + " / partsId=" + e.getPartsId());
            Integer auditStatus = e.getAuditStatus();
            String statusText = auditStatus == null ? "待处理" : (auditStatus == 0 ? "待处理" : (auditStatus == 1 ? "待审核" : (auditStatus == 2 ? "已通过" : "已驳回")));
            t.put("statusText", statusText);
            t.put("createTime", e.getCreateTime());
            tasks.add(t);
            if (tasks.size() >= limit) {
                break;
            }
        }
        return Result.success(tasks);
    }

    @Operation(summary = "工作台批次列表（含进度摘要）")
    @GetMapping("/workbench/batches")
    public Result<List<Map<String, Object>>> getWorkbenchBatches() {
        List<PartsInfo> all = partsInfoMapper.selectList(new LambdaQueryWrapper<PartsInfo>()
                .isNotNull(PartsInfo::getBatchNumber)
                .orderByDesc(PartsInfo::getCreateTime));
        List<String> batchNumbers = new ArrayList<>();
        for (PartsInfo p : all) {
            if (p.getBatchNumber() != null && !batchNumbers.contains(p.getBatchNumber())) {
                batchNumbers.add(p.getBatchNumber());
            }
        }
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (String batchNumber : batchNumbers) {
            summaries.add(buildBatchSummary(batchNumber));
        }
        return Result.success(summaries);
    }

    @Operation(summary = "工作台批次流程视图")
    @GetMapping("/workbench/batch-overview")
    public Result<Map<String, Object>> getBatchOverview(@RequestParam String batchNumber) {
        List<PartsInfo> partsMeta = partsInfoMapper.selectList(new LambdaQueryWrapper<PartsInfo>()
                .eq(PartsInfo::getBatchNumber, batchNumber)
                .orderByDesc(PartsInfo::getCreateTime)
                .orderByDesc(PartsInfo::getId));
        BigDecimal plannedQuantitySum = BigDecimal.ZERO;
        for (PartsInfo pi : partsMeta) {
            if (pi.getQuantity() != null) {
                plannedQuantitySum = plannedQuantitySum.add(pi.getQuantity());
            }
        }
        List<Map<String, Object>> partsInfoRows = new ArrayList<>();
        for (PartsInfo pi : partsMeta) {
            Map<String, Object> pr = new HashMap<>();
            pr.put("id", pi.getId());
            pr.put("partsCode", pi.getPartsCode());
            pr.put("partsName", pi.getPartsName());
            pr.put("batchNumber", pi.getBatchNumber());
            pr.put("manufacturer", pi.getManufacturer());
            pr.put("quantity", pi.getQuantity());
            partsInfoRows.add(pr);
        }

        List<PartsUnit> units = partsUnitMapper.selectList(new LambdaQueryWrapper<PartsUnit>()
                .eq(PartsUnit::getBatchNumber, batchNumber)
                .orderByDesc(PartsUnit::getCreateTime)
                .orderByDesc(PartsUnit::getId));
        if (units.isEmpty()) {
            List<PartsInfo> parts = partsMeta;

            List<Map<String, Object>> items = new ArrayList<>();
            int completed = 0;
            for (PartsInfo p : parts) {
                PartsDid did = partsDidMapper.selectOne(new LambdaQueryWrapper<PartsDid>()
                        .eq(PartsDid::getPartsId, p.getId()).last("LIMIT 1"));
                Long eventCount = traceEventMapper.selectCount(new LambdaQueryWrapper<TraceEvent>()
                        .eq(TraceEvent::getPartsId, p.getId()));
                Long vcCount = verifiableCredentialMapper.selectCount(new LambdaQueryWrapper<VerifiableCredential>()
                        .eq(VerifiableCredential::getPartsId, p.getId()));
                TraceEvent latest = traceEventMapper.selectOne(new LambdaQueryWrapper<TraceEvent>()
                        .eq(TraceEvent::getPartsId, p.getId())
                        .orderByDesc(TraceEvent::getEventTime)
                        .last("LIMIT 1"));

                String stage;
                int progress;
                if (did == null) {
                    stage = "待生成DID";
                    progress = 10;
                } else if (did.getDidStatus() != null && did.getDidStatus() == 2) {
                    stage = "DID冻结";
                    progress = 20;
                } else if (eventCount == 0) {
                    stage = "待记录事件";
                    progress = 40;
                } else if (vcCount == 0) {
                    stage = "待签发VC";
                    progress = 70;
                } else {
                    stage = "流程完成";
                    progress = 100;
                    completed++;
                }

                Map<String, Object> row = new java.util.HashMap<>();
                row.put("partsId", p.getId());
                row.put("partsCode", p.getPartsCode());
                row.put("partsName", p.getPartsName());
                row.put("did", did == null ? null : did.getDid());
                row.put("didStatus", did == null ? null : did.getDidStatus());
                row.put("eventCount", eventCount);
                row.put("vcCount", vcCount);
                row.put("latestEventType", latest == null ? null : latest.getEventType());
                row.put("latestEventTime", latest == null ? null : latest.getEventTime());
                row.put("stage", stage);
                row.put("progress", progress);
                items.add(row);
            }
            Map<String, Object> res = new java.util.HashMap<>();
            res.put("batchNumber", batchNumber);
            res.put("totalParts", parts.size());
            res.put("completedParts", completed);
            res.put("completionRate", parts.isEmpty() ? 0 : (completed * 100 / parts.size()));
            res.put("items", items);
            res.put("partsInfoRowCount", partsMeta.size());
            res.put("plannedQuantitySum", plannedQuantitySum);
            res.put("partsInfoRows", partsInfoRows);
            return Result.success(res);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        int completed = 0;
        for (PartsUnit u : units) {
            PartsInfo p = partsInfoMapper.selectById(u.getPartsId());
            PartsDid did = partsDidMapper.selectOne(new LambdaQueryWrapper<PartsDid>()
                    .eq(PartsDid::getUnitId, u.getId()).last("LIMIT 1"));
            Long eventCount = traceEventMapper.selectCount(new LambdaQueryWrapper<TraceEvent>()
                    .eq(TraceEvent::getUnitId, u.getId()));
            Long vcCount = verifiableCredentialMapper.selectCount(new LambdaQueryWrapper<VerifiableCredential>()
                    .eq(VerifiableCredential::getUnitId, u.getId()));
            TraceEvent latest = traceEventMapper.selectOne(new LambdaQueryWrapper<TraceEvent>()
                    .eq(TraceEvent::getUnitId, u.getId())
                    .orderByDesc(TraceEvent::getEventTime)
                    .last("LIMIT 1"));

            String stage;
            int progress;
            if (did == null) {
                stage = "待生成DID";
                progress = 10;
            } else if (did.getDidStatus() != null && did.getDidStatus() == 2) {
                stage = "DID冻结";
                progress = 20;
            } else if (eventCount == 0) {
                stage = "待记录事件";
                progress = 40;
            } else if (vcCount == 0) {
                stage = "待签发VC";
                progress = 70;
            } else {
                stage = "流程完成";
                progress = 100;
                completed++;
            }

            Map<String, Object> row = new java.util.HashMap<>();
            row.put("unitId", u.getId());
            row.put("unitCode", u.getUnitCode());
            row.put("partsId", u.getPartsId());
            row.put("partsCode", p == null ? null : p.getPartsCode());
            row.put("partsName", p == null ? null : p.getPartsName());
            row.put("did", did == null ? null : did.getDid());
            row.put("didStatus", did == null ? null : did.getDidStatus());
            row.put("eventCount", eventCount);
            row.put("vcCount", vcCount);
            row.put("latestEventType", latest == null ? null : latest.getEventType());
            row.put("latestEventTime", latest == null ? null : latest.getEventTime());
            row.put("stage", stage);
            row.put("progress", progress);
            items.add(row);
        }
        Map<String, Object> res = new java.util.HashMap<>();
        res.put("batchNumber", batchNumber);
        res.put("totalParts", units.size());
        res.put("completedParts", completed);
        res.put("completionRate", units.isEmpty() ? 0 : (completed * 100 / units.size()));
        res.put("items", items);
        res.put("partsInfoRowCount", partsMeta.size());
        res.put("plannedQuantitySum", plannedQuantitySum);
        res.put("partsInfoRows", partsInfoRows);
        return Result.success(res);
    }

    @Operation(summary = "工作台批次单件详情")
    @GetMapping("/workbench/batch-units")
    public Result<List<Map<String, Object>>> getBatchUnits(@RequestParam String batchNumber) {
        List<PartsUnit> units = partsUnitMapper.selectList(new LambdaQueryWrapper<PartsUnit>()
                .eq(PartsUnit::getBatchNumber, batchNumber)
                .orderByDesc(PartsUnit::getCreateTime)
                .orderByDesc(PartsUnit::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (PartsUnit u : units) {
            PartsDid did = partsDidMapper.selectOne(new LambdaQueryWrapper<PartsDid>()
                    .eq(PartsDid::getUnitId, u.getId()).last("LIMIT 1"));
            Long eventCount = traceEventMapper.selectCount(new LambdaQueryWrapper<TraceEvent>().eq(TraceEvent::getUnitId, u.getId()));
            Long vcCount = verifiableCredentialMapper.selectCount(new LambdaQueryWrapper<VerifiableCredential>().eq(VerifiableCredential::getUnitId, u.getId()));
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("unitId", u.getId());
            row.put("unitCode", u.getUnitCode());
            row.put("partsId", u.getPartsId());
            row.put("did", did == null ? null : did.getDid());
            row.put("didStatus", did == null ? null : did.getDidStatus());
            row.put("eventCount", eventCount);
            row.put("vcCount", vcCount);
            result.add(row);
        }
        return Result.success(result);
    }

    @Operation(summary = "工作台批次动作（批量记录+可选批量签发）")
    @PostMapping("/workbench/batch-action")
    public Result<Map<String, Object>> submitBatchAction(
            HttpServletRequest request,
            @Validated @RequestBody WorkbenchBatchActionDTO dto) {
        String actionType = dto.getActionType() == null ? "" : dto.getActionType().toLowerCase();
        if ("production".equals(actionType)) {
            requireAnyRole(request, "admin", "manufacturer");
        } else if ("quality".equals(actionType)) {
            requireAnyRole(request, "admin", "quality");
        } else if ("logistics".equals(actionType)) {
            requireAnyRole(request, "admin", "logistics");
        } else {
            throw new BusinessException("不支持的批次动作类型");
        }

        List<PartsUnit> units = partsUnitMapper.selectList(new LambdaQueryWrapper<PartsUnit>()
                .eq(PartsUnit::getBatchNumber, dto.getBatchNumber())
                .orderByAsc(PartsUnit::getId));
        List<PartsInfo> parts = partsInfoMapper.selectList(new LambdaQueryWrapper<PartsInfo>()
                .eq(PartsInfo::getBatchNumber, dto.getBatchNumber()).orderByAsc(PartsInfo::getId));
        if (parts.isEmpty() && units.isEmpty()) {
            throw new BusinessException("该批次没有零部件数据");
        }

        List<Long> filterPartsIds = dto.getPartsIds();
        if (filterPartsIds != null && !filterPartsIds.isEmpty()) {
            java.util.Set<Long> allowed = new java.util.HashSet<>(filterPartsIds);
            if (!units.isEmpty()) {
                List<PartsUnit> filteredU = new ArrayList<>();
                for (PartsUnit u : units) {
                    if (u.getPartsId() != null && allowed.contains(u.getPartsId())) {
                        filteredU.add(u);
                    }
                }
                units = filteredU;
            } else {
                List<PartsInfo> filteredP = new ArrayList<>();
                for (PartsInfo p : parts) {
                    if (allowed.contains(p.getId())) {
                        filteredP.add(p);
                    }
                }
                parts = filteredP;
            }
            if (parts.isEmpty() && units.isEmpty()) {
                throw new BusinessException("所选零部件主数据在当前批次下没有可处理单件");
            }
        }

        List<Long> filterUnitIds = dto.getUnitIds();
        if (filterUnitIds != null && !filterUnitIds.isEmpty()) {
            java.util.Set<Long> allowedUnits = new java.util.HashSet<>(filterUnitIds);
            List<PartsUnit> filteredUnits = new ArrayList<>();
            for (PartsUnit u : units) {
                if (allowedUnits.contains(u.getId())) {
                    filteredUnits.add(u);
                }
            }
            units = filteredUnits;
            if (units.isEmpty()) {
                throw new BusinessException("所选单件在当前批次下不可处理");
            }
        }

        int success = 0;
        int failed = 0;
        List<Map<String, Object>> successes = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        if (!units.isEmpty()) {
            for (PartsUnit u : units) {
                try {
                    assertUnitEligibleForAction(u.getId(), actionType);
                    TraceEvent event = doSingleByActionType(u.getPartsId(), u.getId(), actionType, dto);
                    VerifiableCredential vc = issueVcForEvent(event, actionType);
                    success++;
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("unitId", u.getId());
                    item.put("unitCode", u.getUnitCode());
                    item.put("partsId", u.getPartsId());
                    item.put("eventId", event.getId());
                    item.put("vcId", vc != null ? vc.getVcId() : null);
                    item.put("message", "事件存证与 VC 签发完成");
                    successes.add(item);
                } catch (Exception ex) {
                    failed++;
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("unitId", u.getId());
                    item.put("unitCode", u.getUnitCode());
                    item.put("message", ex.getMessage());
                    failures.add(item);
                }
            }
        } else {
            for (PartsInfo p : parts) {
                try {
                    TraceEvent event = doSingleByActionType(p.getId(), null, actionType, dto);
                    VerifiableCredential vc = issueVcForEvent(event, actionType);
                    success++;
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("partsId", p.getId());
                    item.put("partsCode", p.getPartsCode());
                    item.put("eventId", event.getId());
                    item.put("vcId", vc != null ? vc.getVcId() : null);
                    item.put("message", "事件存证与 VC 签发完成");
                    successes.add(item);
                } catch (Exception ex) {
                    failed++;
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("partsId", p.getId());
                    item.put("partsCode", p.getPartsCode());
                    item.put("message", ex.getMessage());
                    failures.add(item);
                }
            }
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("batchNumber", dto.getBatchNumber());
        result.put("total", !units.isEmpty() ? units.size() : parts.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("successes", successes);
        result.put("failures", failures);
        return Result.success("批次处理完成", result);
    }

    @Operation(summary = "标记异常事件")
    @PostMapping("/event/{eventId}/abnormal")
    public Result<TraceEvent> markAbnormal(HttpServletRequest request, @PathVariable Long eventId, @RequestParam String reason) {
        requireAnyRole(request, "admin", "manufacturer", "quality", "logistics");
        return Result.success(traceEventService.markEventAbnormal(eventId, reason));
    }

    @Operation(summary = "审核异常事件")
    @PostMapping("/event/{eventId}/audit")
    public Result<TraceEvent> auditEvent(
            HttpServletRequest request,
            @PathVariable Long eventId,
            @RequestParam Integer auditStatus,
            @RequestParam(required = false) String comment) {
        requireAnyRole(request, "admin", "viewer");
        return Result.success(traceEventService.auditEvent(eventId, auditStatus, comment));
    }

    // ==================== 原有接口 ====================

    /**
     * 记录生产事件
     */
    @Operation(summary = "记录生产事件")
    @PostMapping("/event/production")
    public Result<TraceEvent> recordProductionEvent(HttpServletRequest request, @Validated @RequestBody ProductionEventDTO eventDTO) {
        requireAnyRole(request, "admin", "manufacturer");
        TraceEvent traceEvent = traceEventService.recordProductionEvent(eventDTO);
        return Result.success("生产事件记录成功", traceEvent);
    }

    @Operation(summary = "批量记录生产事件")
    @PostMapping("/event/production/batch")
    public Result<List<TraceEvent>> recordProductionBatch(HttpServletRequest request, @RequestBody List<ProductionEventDTO> events) {
        requireAnyRole(request, "admin", "manufacturer");
        List<TraceEvent> result = new ArrayList<>();
        for (ProductionEventDTO dto : events) {
            result.add(traceEventService.recordProductionEvent(dto));
        }
        return Result.success(result);
    }

    /**
     * 记录质检事件
     */
    @Operation(summary = "记录质检事件")
    @PostMapping("/event/quality")
    public Result<TraceEvent> recordQualityEvent(HttpServletRequest request, @Validated @RequestBody QualityEventDTO eventDTO) {
        requireAnyRole(request, "admin", "quality");
        TraceEvent traceEvent = traceEventService.recordQualityEvent(eventDTO);
        return Result.success("质检事件记录成功", traceEvent);
    }

    @Operation(summary = "批量记录质检事件")
    @PostMapping("/event/quality/batch")
    public Result<List<TraceEvent>> recordQualityBatch(HttpServletRequest request, @RequestBody List<QualityEventDTO> events) {
        requireAnyRole(request, "admin", "quality");
        List<TraceEvent> result = new ArrayList<>();
        for (QualityEventDTO dto : events) {
            result.add(traceEventService.recordQualityEvent(dto));
        }
        return Result.success(result);
    }

    /**
     * 记录物流事件
     */
    @Operation(summary = "记录物流事件")
    @PostMapping("/event/logistics")
    public Result<TraceEvent> recordLogisticsEvent(HttpServletRequest request, @Validated @RequestBody LogisticsEventDTO eventDTO) {
        requireAnyRole(request, "admin", "logistics");
        TraceEvent traceEvent = traceEventService.recordLogisticsEvent(eventDTO);
        return Result.success("物流事件记录成功", traceEvent);
    }

    @Operation(summary = "批量记录物流事件")
    @PostMapping("/event/logistics/batch")
    public Result<List<TraceEvent>> recordLogisticsBatch(HttpServletRequest request, @RequestBody List<LogisticsEventDTO> events) {
        requireAnyRole(request, "admin", "logistics");
        List<TraceEvent> result = new ArrayList<>();
        for (LogisticsEventDTO dto : events) {
            result.add(traceEventService.recordLogisticsEvent(dto));
        }
        return Result.success(result);
    }

    /**
     * 获取零部件的所有事"
     */
    @Operation(summary = "获取零部件的所有事")
    @GetMapping("/event/parts/{partsId}")
    public Result<List<TraceEvent>> getPartEvents(@PathVariable Long partsId) {
        List<TraceEvent> events = traceEventService.getPartEvents(partsId);
        return Result.success(events);
    }

    /**
     * 获取溯源链条
     */
    @Operation(summary = "获取溯源链条")
    @GetMapping("/event/chain/{partsId}")
    public Result<List<TraceEvent>> getTraceChain(@PathVariable Long partsId) {
        List<TraceEvent> chain = traceEventService.getTraceChain(partsId);
        return Result.success(chain);
    }

    /**
     * 获取事件详情
     */
    @Operation(summary = "获取事件详情")
    @GetMapping("/event/{eventId}")
    public Result<TraceEventDetailVO> getEventDetail(@PathVariable Long eventId) {
        TraceEventDetailVO detail = traceEventService.getEventDetail(eventId);
        return Result.success(detail);
    }

    /**
     * 删除事件
     */
    @Operation(summary = "删除事件")
    @DeleteMapping("/event/{eventId}")
    public Result<Void> deleteEvent(@PathVariable Long eventId) {
        traceEventService.deleteEvent(eventId);
        return Result.successMsg("事件删除成功");
    }

    private void requireAnyRole(HttpServletRequest request, String... roleCodes) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("未登录或登录已过期");
        }
        for (String roleCode : roleCodes) {
            if (roleService.hasRole(userId, roleCode)) {
                return;
            }
        }
        throw new BusinessException("无权限执行该操作");
    }

    private void assertUnitEligibleForAction(Long unitId, String actionType) {
        PartsDid did = partsDidMapper.selectOne(new LambdaQueryWrapper<PartsDid>()
                .eq(PartsDid::getUnitId, unitId).last("LIMIT 1"));
        if (did == null || (did.getDidStatus() != null && did.getDidStatus() == 2)) {
            throw new BusinessException("单件 DID 未激活或已冻结");
        }
        TraceEvent latest = traceEventMapper.selectOne(new LambdaQueryWrapper<TraceEvent>()
                .eq(TraceEvent::getUnitId, unitId)
                .orderByDesc(TraceEvent::getEventTime)
                .last("LIMIT 1"));
        Long vcCount = verifiableCredentialMapper.selectCount(new LambdaQueryWrapper<VerifiableCredential>()
                .eq(VerifiableCredential::getUnitId, unitId));
        String phase = resolveUnitPhase(latest, vcCount);
        if ("done".equals(phase) || "none".equals(phase)) {
            throw new BusinessException("单件当前阶段不可登记事件");
        }
        if (!actionType.equals(phase)) {
            throw new BusinessException("单件当前阶段不允许登记「" + actionType + "」事件，期望「" + phase + "」");
        }
    }

    private String resolveUnitPhase(TraceEvent latest, Long vcCount) {
        if (latest == null || latest.getEventType() == null) {
            return "production";
        }
        String t = latest.getEventType().toLowerCase();
        if (vcCount != null && vcCount > 0 && "logistics".equals(t)) {
            return "done";
        }
        if ("quality".equals(t)) {
            return "logistics";
        }
        if ("production".equals(t)) {
            return "quality";
        }
        return "production";
    }

    private TraceEvent doSingleByActionType(Long partsId, Long unitId, String actionType, WorkbenchBatchActionDTO dto) {
        if ("production".equals(actionType)) {
            ProductionEventDTO p = new ProductionEventDTO();
            p.setPartsId(partsId);
            p.setUnitId(unitId);
            p.setEventName(dto.getEventName() == null ? "批次生产记录" : dto.getEventName());
            p.setEventTime(dto.getEventTime() == null ? new java.util.Date() : dto.getEventTime());
            p.setEventLocation(dto.getEventLocation());
            p.setOperatorId(dto.getOperatorId() == null ? 1L : dto.getOperatorId());
            p.setOperatorName(dto.getOperatorName() == null ? "系统批处理" : dto.getOperatorName());
            p.setOrganization(dto.getOrganization());
            p.setEvidenceUrls(dto.getEvidenceUrls());
            p.setProductionLine(dto.getProductionLine());
            p.setProductionBatch(dto.getProductionBatch());
            return traceEventService.recordProductionEvent(p);
        }
        if ("quality".equals(actionType)) {
            QualityEventDTO q = new QualityEventDTO();
            q.setPartsId(partsId);
            q.setUnitId(unitId);
            q.setEventName(dto.getEventName() == null ? "批次质检记录" : dto.getEventName());
            q.setEventTime(dto.getEventTime() == null ? new java.util.Date() : dto.getEventTime());
            q.setEventLocation(dto.getEventLocation());
            q.setOperatorId(dto.getOperatorId() == null ? 1L : dto.getOperatorId());
            q.setOperatorName(dto.getOperatorName() == null ? "系统批处理" : dto.getOperatorName());
            q.setOrganization(dto.getOrganization());
            q.setEvidenceUrls(dto.getEvidenceUrls());
            q.setInspectionType(dto.getInspectionType() == null ? "批次质检" : dto.getInspectionType());
            q.setInspectionResult(dto.getInspectionResult() == null ? "合格" : dto.getInspectionResult());
            q.setInspector(dto.getOperatorName() == null ? "系统批处理" : dto.getOperatorName());
            return traceEventService.recordQualityEvent(q);
        }
        LogisticsEventDTO l = new LogisticsEventDTO();
        l.setPartsId(partsId);
        l.setUnitId(unitId);
        l.setEventName(dto.getEventName() == null ? "批次物流记录" : dto.getEventName());
        l.setEventTime(dto.getEventTime() == null ? new java.util.Date() : dto.getEventTime());
        l.setEventLocation(dto.getEventLocation());
        l.setOperatorId(dto.getOperatorId() == null ? 1L : dto.getOperatorId());
        l.setOperatorName(dto.getOperatorName() == null ? "系统批处理" : dto.getOperatorName());
        l.setOrganization(dto.getOrganization());
        l.setEvidenceUrls(dto.getEvidenceUrls());
        l.setLogisticsType(dto.getLogisticsType() == null ? "批次物流" : dto.getLogisticsType());
        l.setTrackingNumber(dto.getTrackingNumber());
        return traceEventService.recordLogisticsEvent(l);
    }

    /** 事件与 VC 一体签发（DID + VC + 链上锚定闭环） */
    private VerifiableCredential issueVcForEvent(TraceEvent event, String actionType) {
        if ("production".equals(actionType)) {
            return credentialService.issueProductionCertificate(event.getId());
        }
        if ("quality".equals(actionType)) {
            return credentialService.issueQualityReport(event.getId());
        }
        return credentialService.issueTransferCertificate(event.getId());
    }

    private Map<String, Object> buildBatchSummary(String batchNumber) {
        List<PartsInfo> partsMeta = partsInfoMapper.selectList(new LambdaQueryWrapper<PartsInfo>()
                .eq(PartsInfo::getBatchNumber, batchNumber)
                .orderByDesc(PartsInfo::getCreateTime)
                .orderByDesc(PartsInfo::getId));
        BigDecimal plannedQuantitySum = BigDecimal.ZERO;
        for (PartsInfo pi : partsMeta) {
            if (pi.getQuantity() != null) {
                plannedQuantitySum = plannedQuantitySum.add(pi.getQuantity());
            }
        }
        PartsInfo first = partsMeta.isEmpty() ? null : partsMeta.get(0);

        List<PartsUnit> units = partsUnitMapper.selectList(new LambdaQueryWrapper<PartsUnit>()
                .eq(PartsUnit::getBatchNumber, batchNumber)
                .orderByAsc(PartsUnit::getId));

        int totalParts = 0;
        int completedParts = 0;
        int withDidCount = 0;
        String minPhase = "done";

        if (units.isEmpty()) {
            totalParts = partsMeta.size();
            for (PartsInfo p : partsMeta) {
                PartsDid did = partsDidMapper.selectOne(new LambdaQueryWrapper<PartsDid>()
                        .eq(PartsDid::getPartsId, p.getId()).last("LIMIT 1"));
                Long eventCount = traceEventMapper.selectCount(new LambdaQueryWrapper<TraceEvent>()
                        .eq(TraceEvent::getPartsId, p.getId()));
                Long vcCount = verifiableCredentialMapper.selectCount(new LambdaQueryWrapper<VerifiableCredential>()
                        .eq(VerifiableCredential::getPartsId, p.getId()));
                TraceEvent latest = traceEventMapper.selectOne(new LambdaQueryWrapper<TraceEvent>()
                        .eq(TraceEvent::getPartsId, p.getId())
                        .orderByDesc(TraceEvent::getEventTime)
                        .last("LIMIT 1"));
                if (did != null && did.getDid() != null) {
                    withDidCount++;
                }
                String stage = resolveLegacyStage(did, eventCount, vcCount);
                if ("流程完成".equals(stage)) {
                    completedParts++;
                }
                String phase = resolveUnitPhase(
                        latest == null ? null : latest.getEventType(),
                        did != null && did.getDid() != null,
                        did == null ? null : did.getDidStatus(),
                        vcCount == null ? 0L : vcCount);
                minPhase = minPhaseByOrder(minPhase, phase);
            }
        } else {
            totalParts = units.size();
            for (PartsUnit u : units) {
                PartsDid did = partsDidMapper.selectOne(new LambdaQueryWrapper<PartsDid>()
                        .eq(PartsDid::getUnitId, u.getId()).last("LIMIT 1"));
                Long eventCount = traceEventMapper.selectCount(new LambdaQueryWrapper<TraceEvent>()
                        .eq(TraceEvent::getUnitId, u.getId()));
                Long vcCount = verifiableCredentialMapper.selectCount(new LambdaQueryWrapper<VerifiableCredential>()
                        .eq(VerifiableCredential::getUnitId, u.getId()));
                TraceEvent latest = traceEventMapper.selectOne(new LambdaQueryWrapper<TraceEvent>()
                        .eq(TraceEvent::getUnitId, u.getId())
                        .orderByDesc(TraceEvent::getEventTime)
                        .last("LIMIT 1"));
                if (did != null && did.getDid() != null) {
                    withDidCount++;
                }
                String stage = resolveLegacyStage(did, eventCount, vcCount);
                if ("流程完成".equals(stage)) {
                    completedParts++;
                }
                String phase = resolveUnitPhase(
                        latest == null ? null : latest.getEventType(),
                        did != null && did.getDid() != null,
                        did == null ? null : did.getDidStatus(),
                        vcCount == null ? 0L : vcCount);
                minPhase = minPhaseByOrder(minPhase, phase);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("batchNumber", batchNumber);
        summary.put("partsCode", first == null ? "-" : first.getPartsCode());
        summary.put("partsName", first == null ? "-" : first.getPartsName());
        summary.put("manufacturer", first == null ? "-" : first.getManufacturer());
        summary.put("plannedQuantity", plannedQuantitySum);
        summary.put("partsInfoRowCount", partsMeta.size());
        summary.put("totalParts", totalParts);
        summary.put("completedParts", completedParts);
        summary.put("completionRate", totalParts == 0 ? 0 : (completedParts * 100 / totalParts));
        summary.put("withDidCount", withDidCount);
        summary.put("currentPhase", phaseText(minPhase));
        summary.put("currentPhaseCode", minPhase);
        return summary;
    }

    private String resolveLegacyStage(PartsDid did, Long eventCount, Long vcCount) {
        long events = eventCount == null ? 0L : eventCount;
        long vcs = vcCount == null ? 0L : vcCount;
        if (did == null) {
            return "待生成DID";
        }
        if (did.getDidStatus() != null && did.getDidStatus() == 2) {
            return "DID冻结";
        }
        if (events == 0) {
            return "待记录事件";
        }
        if (vcs == 0) {
            return "待签发VC";
        }
        return "流程完成";
    }

    private String resolveUnitPhase(String latestEventType, boolean hasDid, Integer didStatus, long vcCount) {
        if (!hasDid || (didStatus != null && didStatus == 2)) {
            return "none";
        }
        String t = latestEventType == null ? "" : latestEventType.toLowerCase();
        if (vcCount > 0 && "logistics".equals(t)) {
            return "done";
        }
        if ("quality".equals(t)) {
            return "logistics";
        }
        if ("production".equals(t)) {
            return "quality";
        }
        return "production";
    }

    private String minPhaseByOrder(String current, String candidate) {
        return phaseOrder(candidate) < phaseOrder(current) ? candidate : current;
    }

    private int phaseOrder(String phase) {
        switch (phase) {
            case "none":
                return 0;
            case "production":
                return 1;
            case "quality":
                return 2;
            case "logistics":
                return 3;
            case "done":
                return 4;
            default:
                return 0;
        }
    }

    private String phaseText(String phase) {
        switch (phase) {
            case "none":
                return "待DID激活";
            case "production":
                return "待生产事件";
            case "quality":
                return "待质检事件";
            case "logistics":
                return "待物流事件";
            case "done":
                return "全流程完成";
            default:
                return "未知";
        }
    }
}





