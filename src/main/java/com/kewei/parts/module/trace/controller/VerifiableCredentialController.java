package com.kewei.parts.module.trace.controller;

import com.kewei.parts.common.BusinessException;
import com.kewei.parts.common.Result;
import com.kewei.parts.module.system.service.RoleService;
import com.kewei.parts.module.trace.entity.VerifiableCredential;
import com.kewei.parts.module.trace.entity.VcVerifyRecord;
import com.kewei.parts.module.trace.service.VerifiableCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 可验证凭证控制器
 */
@Tag(name = "可验证凭证管理", description = "可验证凭证签发与验证接口")
@RestController
@RequestMapping("/api/trace/credential")
public class VerifiableCredentialController {

    @Autowired
    private VerifiableCredentialService credentialService;

    @Autowired
    private RoleService roleService;

    /**
     * 签发生产证明
     */
    @Operation(summary = "签发生产证明")
    @PostMapping("/production/{eventId}")
    public Result<VerifiableCredential> issueProductionCertificate(HttpServletRequest request, @PathVariable Long eventId) {
        requireAnyRole(request, "admin", "manufacturer");
        VerifiableCredential credential = credentialService.issueProductionCertificate(eventId);
        return Result.success("生产证明签发成功", credential);
    }

    /**
     * 签发质检报告
     */
    @Operation(summary = "签发质检报告")
    @PostMapping("/quality/{eventId}")
    public Result<VerifiableCredential> issueQualityReport(HttpServletRequest request, @PathVariable Long eventId) {
        requireAnyRole(request, "admin", "quality");
        VerifiableCredential credential = credentialService.issueQualityReport(eventId);
        return Result.success("质检报告签发成功", credential);
    }

    /**
     * 签发流转证明
     */
    @Operation(summary = "签发流转证明")
    @PostMapping("/transfer/{eventId}")
    public Result<VerifiableCredential> issueTransferCertificate(HttpServletRequest request, @PathVariable Long eventId) {
        requireAnyRole(request, "admin", "logistics");
        VerifiableCredential credential = credentialService.issueTransferCertificate(eventId);
        return Result.success("流转证明签发成功", credential);
    }

    /**
     * 签发综合溯源凭证
     */
    @Operation(summary = "签发综合溯源凭证")
    @PostMapping("/comprehensive/{partsId}")
    public Result<VerifiableCredential> issueComprehensiveCredential(@PathVariable Long partsId) {
        VerifiableCredential credential = credentialService.issueComprehensiveCredential(partsId);
        return Result.success("综合溯源凭证签发成功", credential);
    }

    /**
     * 验证凭证
     */
    @Operation(summary = "验证凭证")
    @GetMapping("/verify/{vcId}")
    public Result<Object> verifyCredential(@PathVariable String vcId) {
        return Result.success(credentialService.verifyCredentialStandard(vcId));
    }

    /**
     * 撤销凭证
     */
    @Operation(summary = "撤销凭证")
    @PutMapping("/revoke/{vcId}")
    public Result<Void> revokeCredential(@PathVariable String vcId, @RequestParam String reason) {
        credentialService.revokeCredential(vcId, reason);
        return Result.successMsg("凭证撤销成功");
    }

    /**
     * 获取零部件的所有凭证
     */
    @Operation(summary = "获取零部件的所有凭证")
    @GetMapping("/parts/{partsId}")
    public Result<List<VerifiableCredential>> getPartsCredentials(@PathVariable Long partsId) {
        List<VerifiableCredential> credentials = credentialService.getPartsCredentials(partsId);
        return Result.success(credentials);
    }

    /**
     * 获取事件的凭证
     */
    @Operation(summary = "获取事件的凭证")
    @GetMapping("/event/{eventId}")
    public Result<VerifiableCredential> getEventCredential(@PathVariable Long eventId) {
        VerifiableCredential credential = credentialService.getEventCredential(eventId);
        return Result.success(credential);
    }

    @Operation(summary = "查询VC验证记录")
    @GetMapping("/verify-records")
    public Result<List<VcVerifyRecord>> getVerifyRecords(@RequestParam(required = false) String vcId) {
        return Result.success(credentialService.getVerifyRecords(vcId));
    }

    @Operation(summary = "批量签发质检VC")
    @PostMapping("/quality/batch")
    public Result<List<VerifiableCredential>> issueQualityBatch(HttpServletRequest request, @RequestBody List<Long> eventIds) {
        requireAnyRole(request, "admin", "quality");
        List<VerifiableCredential> list = new ArrayList<>();
        for (Long eventId : eventIds) {
            list.add(credentialService.issueQualityReport(eventId));
        }
        return Result.success(list);
    }

    @Operation(summary = "批量验证VC")
    @PostMapping("/verify/batch")
    public Result<List<Object>> verifyBatch(@RequestBody List<String> vcIds) {
        List<Object> list = new ArrayList<>();
        for (String vcId : vcIds) {
            list.add(credentialService.verifyCredentialStandard(vcId));
        }
        return Result.success(list);
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
