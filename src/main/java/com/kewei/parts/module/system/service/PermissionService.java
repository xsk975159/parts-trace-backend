package com.kewei.parts.module.system.service;

import com.kewei.parts.module.system.entity.SysPermission;

import java.util.List;

/**
 * 权限服务接口
 */
public interface PermissionService {

    /**
     * 获取所有权限列"
     */
    List<SysPermission> getAllPermissions();

    /**
     * 获取权限"
     */
    List<SysPermission> getPermissionTree();

    /**
     * 根据角色ID获取权限列表
     */
    List<SysPermission> getPermissionsByRoleId(Long roleId);

    /**
     * 根据用户ID获取权限列表
     */
    List<SysPermission> getPermissionsByUserId(Long userId);

    /**
     * 创建权限
     */
    void createPermission(SysPermission permission);

    /**
     * 更新权限
     */
    void updatePermission(SysPermission permission);

    /**
     * 删除权限
     */
    void deletePermission(Long permissionId);

    /**
     * 为角色分配权"
     */
    void assignPermissionsToRole(Long roleId, List<Long> permissionIds);

    /**
     * 获取权限树（带子权限"
     */
    List<SysPermission> buildPermissionTree(List<SysPermission> permissions);
}





