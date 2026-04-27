package com.kewei.parts.module.system.controller;

import com.kewei.parts.common.Result;
import com.kewei.parts.module.system.entity.SysRole;
import com.kewei.parts.module.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 角色管理控制器
 */
@Tag(name = "角色管理", description = "角色管理、权限分配等接口")
@RestController
@RequestMapping("/api/role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @Operation(summary = "获取角色列表")
    @GetMapping("/list")
    public Result<Object> getRoleList() {
        List<SysRole> roleList = roleService.getAllRoles();
        java.util.Map<String, Object> data = new java.util.HashMap<String, Object>();
        data.put("list", roleList);
        data.put("total", roleList.size());
        return Result.success(data);
    }

    @Operation(summary = "根据ID获取角色信息")
    @GetMapping("/{roleId}")
    public Result<SysRole> getRoleById(@PathVariable Long roleId) {
        return Result.success(roleService.getRoleById(roleId));
    }

    @Operation(summary = "创建角色")
    @PostMapping("/create")
    public Result<Void> createRole(@Valid @RequestBody SysRole role) {
        roleService.createRole(role);
        return Result.successMsg("角色创建成功");
    }

    @Operation(summary = "更新角色")
    @PutMapping("/update")
    public Result<Void> updateRole(@RequestBody SysRole role) {
        roleService.updateRole(role);
        return Result.successMsg("角色更新成功");
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/delete/{roleId}")
    public Result<Void> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return Result.successMsg("角色删除成功");
    }

    @Operation(summary = "为用户分配角色")
    @PostMapping("/assign")
    public Result<Void> assignRoleToUser(@RequestParam Long userId, @RequestParam Long roleId) {
        roleService.assignRoleToUser(userId, roleId);
        return Result.successMsg("角色分配成功");
    }

    @Operation(summary = "移除用户角色")
    @DeleteMapping("/remove")
    public Result<Void> removeRoleFromUser(@RequestParam Long userId, @RequestParam Long roleId) {
        roleService.removeRoleFromUser(userId, roleId);
        return Result.successMsg("角色移除成功");
    }

    @Operation(summary = "获取用户的所有角色")
    @GetMapping("/user/{userId}")
    public Result<List<SysRole>> getUserRoles(@PathVariable Long userId) {
        return Result.success(roleService.getUserRoles(userId));
    }
}
