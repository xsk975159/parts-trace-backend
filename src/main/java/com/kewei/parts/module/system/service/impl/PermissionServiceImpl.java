package com.kewei.parts.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.module.system.entity.SysPermission;
import com.kewei.parts.module.system.entity.SysRolePermission;
import com.kewei.parts.module.system.mapper.SysPermissionMapper;
import com.kewei.parts.module.system.mapper.SysRolePermissionMapper;
import com.kewei.parts.module.system.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    @Autowired
    private SysPermissionMapper permissionMapper;

    @Autowired
    private SysRolePermissionMapper rolePermissionMapper;

    @Override
    public List<SysPermission> getAllPermissions() {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermission::getStatus, 1);
        wrapper.orderByAsc(SysPermission::getSort);
        return permissionMapper.selectList(wrapper);
    }

    @Override
    public List<SysPermission> getPermissionTree() {
        List<SysPermission> permissions = getAllPermissions();
        return buildPermissionTree(permissions);
    }

    @Override
    public List<SysPermission> getPermissionsByRoleId(Long roleId) {
        return permissionMapper.selectPermissionsByRoleId(roleId);
    }

    @Override
    public List<SysPermission> getPermissionsByUserId(Long userId) {
        return permissionMapper.selectPermissionsByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPermission(SysPermission permission) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermission::getPermissionCode, permission.getPermissionCode());
        SysPermission existPermission = permissionMapper.selectOne(wrapper);
        if (existPermission != null) {
            throw new BusinessException("权限编码已存在");
        }

        if (permission.getStatus() == null) {
            permission.setStatus(1);
        }
        if (permission.getSort() == null) {
            permission.setSort(0);
        }

        int result = permissionMapper.insert(permission);
        if (result <= 0) {
            throw new BusinessException("创建权限失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermission(SysPermission permission) {
        SysPermission existPermission = permissionMapper.selectById(permission.getId());
        if (existPermission == null) {
            throw new BusinessException("权限不存在");
        }

        int result = permissionMapper.updateById(permission);
        if (result <= 0) {
            throw new BusinessException("更新权限失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermission(Long permissionId) {
        SysPermission permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new BusinessException("权限不存在");
        }

        rolePermissionMapper.deleteByPermissionId(permissionId);

        int result = permissionMapper.deleteById(permissionId);
        if (result <= 0) {
            throw new BusinessException("删除权限失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissionsToRole(Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.deleteByRoleId(roleId);

        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                SysRolePermission rolePermission = new SysRolePermission();
                rolePermission.setRoleId(roleId);
                rolePermission.setPermissionId(permissionId);
                rolePermissionMapper.insert(rolePermission);
            }
        }
    }

    @Override
    public List<SysPermission> buildPermissionTree(List<SysPermission> permissions) {
        List<SysPermission> rootPermissions = permissions.stream()
                .filter(p -> p.getParentId() == null || p.getParentId() == 0)
                .collect(Collectors.toList());

        for (SysPermission root : rootPermissions) {
            root.setChildren(buildChildren(root.getId(), permissions));
        }

        return rootPermissions;
    }

    /**
     * 递归构建子权限
     */
    private List<SysPermission> buildChildren(Long parentId, List<SysPermission> permissions) {
        List<SysPermission> children = permissions.stream()
                .filter(p -> p.getParentId() != null && p.getParentId().equals(parentId))
                .collect(Collectors.toList());

        for (SysPermission child : children) {
            child.setChildren(buildChildren(child.getId(), permissions));
        }

        return children;
    }
}
