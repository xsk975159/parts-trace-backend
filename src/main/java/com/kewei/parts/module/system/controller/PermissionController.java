package com.kewei.parts.module.system.controller;

import com.kewei.parts.common.Result;
import com.kewei.parts.module.system.entity.SysPermission;
import com.kewei.parts.module.system.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 权限管理控制器
 */
@Tag(name = "权限管理", description = "权限相关接口")
@RestController
@RequestMapping("/api/permission")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @Operation(summary = "获取权限列表")
    @GetMapping("/list")
    public Result<Object> getPermissionList() {
        List<SysPermission> permissions = permissionService.getAllPermissions();
        java.util.Map<String, Object> data = new java.util.HashMap<String, Object>();
        data.put("list", permissions);
        data.put("total", permissions.size());
        return Result.success(data);
    }

    @Operation(summary = "获取权限树")
    @GetMapping("/tree")
    public Result<List<SysPermission>> getPermissionTree() {
        return Result.success(permissionService.getPermissionTree());
    }

    @Operation(summary = "根据角色ID获取权限列表")
    @GetMapping("/role/{roleId}")
    public Result<List<SysPermission>> getPermissionsByRole(@PathVariable Long roleId) {
        return Result.success(permissionService.getPermissionsByRoleId(roleId));
    }

    @Operation(summary = "创建权限")
    @PostMapping("/create")
    public Result<Void> createPermission(@Valid @RequestBody SysPermission permission) {
        permissionService.createPermission(permission);
        return Result.successMsg("权限创建成功");
    }

    @Operation(summary = "更新权限")
    @PutMapping("/update")
    public Result<Void> updatePermission(@RequestBody SysPermission permission) {
        permissionService.updatePermission(permission);
        return Result.successMsg("权限更新成功");
    }

    @Operation(summary = "删除权限")
    @DeleteMapping("/delete/{permissionId}")
    public Result<Void> deletePermission(@PathVariable Long permissionId) {
        permissionService.deletePermission(permissionId);
        return Result.successMsg("权限删除成功");
    }

    @Operation(summary = "为角色分配权限")
    @PostMapping("/assign")
    public Result<Void> assignPermissionsToRole(@RequestParam Long roleId, @RequestBody List<Long> permissionIds) {
        permissionService.assignPermissionsToRole(roleId, permissionIds);
        return Result.successMsg("权限分配成功");
    }
}
