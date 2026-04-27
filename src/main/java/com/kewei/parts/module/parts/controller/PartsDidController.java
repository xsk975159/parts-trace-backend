package com.kewei.parts.module.parts.controller;

import com.kewei.parts.common.BusinessException;
import com.kewei.parts.common.Result;
import com.kewei.parts.module.parts.dto.PartsDidBindDTO;
import com.kewei.parts.module.parts.dto.PartsDidQueryDTO;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.entity.PartsUnit;
import com.kewei.parts.module.parts.service.PartsService;
import com.kewei.parts.module.parts.vo.DidGenerateTaskVO;
import com.kewei.parts.module.parts.vo.DidValidateVO;
import com.kewei.parts.module.parts.vo.PartsDidVO;
import com.kewei.parts.module.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "零部件DID管理", description = "零部件DID生命周期管理接口")
@RestController
@RequestMapping("/api/parts")
public class PartsDidController {

    @Autowired
    private PartsService partsService;

    @Autowired
    private RoleService roleService;

    @Operation(summary = "同步生成DID")
    @PostMapping("/{partsId}/did/sync")
    public Result<PartsDid> generateDidSync(HttpServletRequest request, @PathVariable Long partsId) {
        requireAnyRole(request, "admin", "manufacturer");
        return Result.success("DID生成成功", partsService.generatePartsDid(partsId));
    }

    @Operation(summary = "批量同步生成DID")
    @PostMapping("/did/batch/sync")
    public Result<List<PartsDid>> generateDidBatchSync(HttpServletRequest request, @RequestBody List<Long> partsIds) {
        requireAnyRole(request, "admin", "manufacturer");
        List<PartsDid> results = new ArrayList<>();
        for (Long partsId : partsIds) {
            results.add(partsService.generatePartsDid(partsId));
        }
        return Result.success(results);
    }

    @Operation(summary = "异步生成DID")
    @PostMapping("/{partsId}/did/async")
    public Result<DidGenerateTaskVO> generateDidAsync(HttpServletRequest request, @PathVariable Long partsId) {
        requireAnyRole(request, "admin", "manufacturer");
        return Result.success(partsService.generatePartsDidAsync(partsId));
    }

    @Operation(summary = "初始化批次单件")
    @PostMapping("/{partsId}/units/init")
    public Result<Integer> initPartsUnits(HttpServletRequest request,
                                          @PathVariable Long partsId,
                                          @RequestParam(required = false) Integer unitCount) {
        requireAnyRole(request, "admin", "manufacturer");
        return Result.success(partsService.initPartsUnits(partsId, unitCount));
    }

    @Operation(summary = "查询批次下单件")
    @GetMapping("/units/by-batch")
    public Result<List<PartsUnit>> getUnitsByBatch(@RequestParam String batchNumber) {
        return Result.success(partsService.getUnitsByBatch(batchNumber));
    }

    @Operation(summary = "生成单件DID")
    @PostMapping("/unit/{unitId}/did/sync")
    public Result<PartsDid> generateUnitDid(HttpServletRequest request, @PathVariable Long unitId) {
        requireAnyRole(request, "admin", "manufacturer");
        return Result.success(partsService.generateUnitDid(unitId));
    }

    @Operation(summary = "查询DID异步任务")
    @GetMapping("/did/task/{taskId}")
    public Result<DidGenerateTaskVO> getTask(@PathVariable String taskId) {
        return Result.success(partsService.getDidGenerateTask(taskId));
    }

    @Operation(summary = "DID列表查询")
    @GetMapping("/did/list")
    public Result<List<PartsDidVO>> listDid(
            @RequestParam(required = false) Long partsId,
            @RequestParam(required = false) String batchNumber,
            @RequestParam(required = false) String did,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "true") Boolean onlyUnit) {
        PartsDidQueryDTO query = new PartsDidQueryDTO();
        query.setPartsId(partsId);
        query.setBatchNumber(batchNumber);
        query.setDid(did);
        query.setDidStatus(status);
        query.setOnlyUnit(onlyUnit);
        return Result.success(partsService.getPartsDidList(query));
    }

    @Operation(summary = "查看DID文档")
    @GetMapping("/did/{didId}/document")
    public Result<String> getDocument(@PathVariable Long didId) {
        return Result.success(partsService.getDidDocument(didId));
    }

    @Operation(summary = "下载DID文档")
    @GetMapping("/did/{didId}/document/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long didId) {
        String content = partsService.getDidDocument(didId);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=did-document-" + didId + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
    }

    @Operation(summary = "绑定物理标识")
    @PostMapping("/did/{didId}/bind")
    public Result<PartsDid> bindDid(@PathVariable Long didId, @Validated @RequestBody PartsDidBindDTO bindDTO) {
        return Result.success(partsService.bindDid(didId, bindDTO));
    }

    @Operation(summary = "解绑物理标识")
    @PostMapping("/did/{didId}/unbind")
    public Result<PartsDid> unbindDid(@PathVariable Long didId) {
        return Result.success(partsService.unbindDid(didId));
    }

    @Operation(summary = "冻结DID")
    @PostMapping("/did/{didId}/freeze")
    public Result<PartsDid> freezeDid(HttpServletRequest request, @PathVariable Long didId, @RequestParam(required = false) String reason) {
        requireAnyRole(request, "admin", "viewer");
        return Result.success(partsService.freezeDid(didId, reason));
    }

    @Operation(summary = "解冻DID")
    @PostMapping("/did/{didId}/unfreeze")
    public Result<PartsDid> unfreezeDid(HttpServletRequest request, @PathVariable Long didId) {
        requireAnyRole(request, "admin", "viewer");
        return Result.success(partsService.unfreezeDid(didId));
    }

    @Operation(summary = "校验DID")
    @GetMapping("/did/validate")
    public Result<DidValidateVO> validateDid(@RequestParam String did) {
        return Result.success(partsService.validateDid(did));
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
}
