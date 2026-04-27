package com.kewei.parts.module.system.service;

import com.kewei.parts.module.system.entity.SysRole;

import java.util.List;

/**
 * 角色服务接口
 */
public interface RoleService {

    /**
     * 获取所有角"
     */
    List<SysRole> getAllRoles();

    /**
     * 根据ID获取角色
     */
    SysRole getRoleById(Long roleId);

    /**
     * 创建角色
     */
    void createRole(SysRole role);

    /**
     * 更新角色
     */
    void updateRole(SysRole role);

    /**
     * 删除角色
     */
    void deleteRole(Long roleId);

    /**
     * 为用户分配角"
     */
    void assignRoleToUser(Long userId, Long roleId);

    /**
     * 移除用户角色
     */
    void removeRoleFromUser(Long userId, Long roleId);

    /**
     * 获取用户的所有角"
     */
    List<SysRole> getUserRoles(Long userId);

    /**
     * 检查用户是否拥有指定角"
     */
    boolean hasRole(Long userId, String roleCode);
}





