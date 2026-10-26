package com.kewei.parts.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kewei.parts.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper接口
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SysUser lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}





