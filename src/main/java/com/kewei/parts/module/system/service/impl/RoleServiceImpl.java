package com.kewei.parts.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.common.ResultCode;
import com.kewei.parts.module.system.mapper.SysRoleMapper;
import com.kewei.parts.module.system.mapper.SysUserRoleMapper;
import com.kewei.parts.module.system.entity.SysRole;
import com.kewei.parts.module.system.entity.SysUserRole;
import com.kewei.parts.module.system.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色服务实现"
 */
@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Override
    public List<SysRole> getAllRoles() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getStatus, 1);
        wrapper.orderByAsc(SysRole::getSort);
        return roleMapper.selectList(wrapper);
    }

    @Override
    public SysRole getRoleById(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ResultCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(SysRole role) {
        // 检查角色编码是否已存在
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getRoleCode, role.getRoleCode());
        SysRole existRole = roleMapper.selectOne(wrapper);
        if (existRole != null) {
            throw new BusinessException("角色编码已存");
        }

        // 设置默认"
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        if (role.getSort() == null) {
            role.setSort(0);
        }

        int result = roleMapper.insert(role);
        if (result <= 0) {
            throw new BusinessException("创建角色失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(SysRole role) {
        SysRole existRole = roleMapper.selectById(role.getId());
        if (existRole == null) {
            throw new BusinessException(ResultCode.ROLE_NOT_FOUND);
        }

        int result = roleMapper.updateById(role);
        if (result <= 0) {
            throw new BusinessException("更新角色失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ResultCode.ROLE_NOT_FOUND);
        }

        // 删除角色关联的用"
        userRoleMapper.deleteByRoleId(roleId);

        // 删除角色
        int result = roleMapper.deleteById(roleId);
        if (result <= 0) {
            throw new BusinessException("删除角色失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoleToUser(Long userId, Long roleId) {
        // 检查角色是否存"
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ResultCode.ROLE_NOT_FOUND);
        }

        // 检查是否已分配
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId);
        wrapper.eq(SysUserRole::getRoleId, roleId);
        SysUserRole existUserRole = userRoleMapper.selectOne(wrapper);
        if (existUserRole != null) {
            throw new BusinessException("用户已拥有该角色");
        }

        // 分配角色
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        int result = userRoleMapper.insert(userRole);
        if (result <= 0) {
            throw new BusinessException("分配角色失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRoleFromUser(Long userId, Long roleId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId);
        wrapper.eq(SysUserRole::getRoleId, roleId);
        
        int result = userRoleMapper.delete(wrapper);
        if (result <= 0) {
            throw new BusinessException("移除角色失败");
        }
    }

    @Override
    public List<SysRole> getUserRoles(Long userId) {
        // 查询用户的角色ID列表
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询角色信息
        return roleMapper.selectBatchIds(roleIds);
    }

    @Override
    public boolean hasRole(Long userId, String roleCode) {
        List<SysRole> roles = getUserRoles(userId);
        return roles.stream().anyMatch(role -> role.getRoleCode().equals(roleCode));
    }
}





