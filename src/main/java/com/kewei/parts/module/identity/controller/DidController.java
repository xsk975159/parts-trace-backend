package com.kewei.parts.module.identity.controller;

import com.kewei.parts.common.Result;
import com.kewei.parts.module.identity.dto.DidBindDTO;
import com.kewei.parts.module.identity.entity.SysUserDid;
import com.kewei.parts.module.identity.service.DidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * DID身份管理控制"
 */
@Tag(name = "DID身份管理", description = "DID身份绑定、查询等接口")
@RestController
@RequestMapping("/api/did")
public class DidController {

    @Autowired
    private DidService didService;

    /**
     * 绑定DID身份
     */
    @Operation(summary = "绑定DID身份")
    @PostMapping("/bind")
    public Result<SysUserDid> bindDid(HttpServletRequest request, @Validated @RequestBody DidBindDTO bindDTO) {
        Long userId = (Long) request.getAttribute("userId");
        SysUserDid userDid = didService.bindDid(userId, bindDTO);
        return Result.success("DID绑定成功", userDid);
    }

    /**
     * 获取用户DID信息
     */
    @Operation(summary = "获取用户DID信息")
    @GetMapping("/info")
    public Result<SysUserDid> getDidInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        SysUserDid userDid = didService.getDidByUserId(userId);
        return Result.success(userDid);
    }

    /**
     * 解绑DID身份
     */
    @Operation(summary = "解绑DID身份")
    @DeleteMapping("/unbind")
    public Result<Void> unbindDid(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        didService.unbindDid(userId);
        return Result.successMsg("DID解绑成功");
    }

    /**
     * 根据DID查询用户信息
     */
    @Operation(summary = "根据DID查询用户信息")
    @GetMapping("/user/{did}")
    public Result<SysUserDid> getUserByDid(@PathVariable String did) {
        SysUserDid userDid = didService.getUserByDid(did);
        return Result.success(userDid);
    }

    /**
     * 验证DID身份
     */
    @Operation(summary = "验证DID身份")
    @PostMapping("/verify")
    public Result<Boolean> verifyDid(@RequestParam String did, @RequestParam String signature) {
        boolean isValid = didService.verifyDid(did, signature);
        return Result.success(isValid);
    }
}





