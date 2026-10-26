package com.kewei.parts.module.identity.controller;

import com.kewei.parts.common.Result;
import com.kewei.parts.module.identity.dto.RsaKeyGenerateDTO;
import com.kewei.parts.module.identity.entity.SysUserRsaKey;
import com.kewei.parts.module.identity.service.RsaKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * RSA密钥管理控制器
 */
@Tag(name = "RSA密钥管理", description = "RSA密钥对生成、查询、管理等接口")
@RestController
@RequestMapping("/api/rsa")
public class RsaKeyController {

    @Autowired
    private RsaKeyService rsaKeyService;
    @Autowired private com.kewei.parts.module.identity.service.impl.RsaKeyServiceImpl keyOperations;

    @PostMapping("/migrate")
    public Result<Integer> migrateOwnKeys(HttpServletRequest request) {
        return Result.success(keyOperations.migrateOwnKeys((Long) request.getAttribute("userId")));
    }

    /**
     * 生成RSA密钥对
     */
    @Operation(summary = "生成RSA密钥对")
    @PostMapping("/generate")
    public Result<SysUserRsaKey> generateKeyPair(HttpServletRequest request,
                                                  @Validated @RequestBody RsaKeyGenerateDTO generateDTO) {
        Long userId = (Long) request.getAttribute("userId");
        generateDTO.setUserId(userId);
        SysUserRsaKey rsaKey = rsaKeyService.generateKeyPair(generateDTO);
        return Result.success("密钥生成成功", rsaKey);
    }

    /**
     * 获取用户的所有密钥
     */
    @Operation(summary = "获取用户的所有密钥")
    @GetMapping("/list")
    public Result<List<SysUserRsaKey>> getKeyList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<SysUserRsaKey> keyList = rsaKeyService.getKeysByUserId(userId);
        return Result.success(keyList);
    }

    /**
     * 获取用户的默认密钥
     */
    @Operation(summary = "获取用户的默认密钥")
    @GetMapping("/default")
    public Result<SysUserRsaKey> getDefaultKey(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        SysUserRsaKey rsaKey = rsaKeyService.getDefaultKeyByUserId(userId);
        return Result.success(rsaKey);
    }

    /**
     * 设置默认密钥
     */
    @Operation(summary = "设置默认密钥")
    @PutMapping("/{keyId}/default")
    public Result<Void> setDefaultKey(HttpServletRequest request, @PathVariable Long keyId) {
        Long userId = (Long) request.getAttribute("userId");
        rsaKeyService.setDefaultKey(userId, keyId);
        return Result.successMsg("设置默认密钥成功");
    }

    /**
     * 启用/禁用密钥
     */
    @Operation(summary = "启用/禁用密钥")
    @PutMapping("/{keyId}/status")
    public Result<Void> updateKeyStatus(HttpServletRequest request,
                                        @PathVariable Long keyId,
                                        @RequestParam Integer status) {
        Long userId = (Long) request.getAttribute("userId");
        rsaKeyService.updateKeyStatus(userId, keyId, status);
        return Result.successMsg("密钥状态更新成功");
    }

    /**
     * 删除密钥
     */
    @Operation(summary = "删除密钥")
    @DeleteMapping("/{keyId}")
    public Result<Void> deleteKey(HttpServletRequest request, @PathVariable Long keyId) {
        Long userId = (Long) request.getAttribute("userId");
        rsaKeyService.deleteKey(userId, keyId);
        return Result.successMsg("密钥删除成功");
    }
}
