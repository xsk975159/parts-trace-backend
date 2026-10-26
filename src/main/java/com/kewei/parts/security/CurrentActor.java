package com.kewei.parts.security;

import com.kewei.parts.common.BusinessException;
import com.kewei.parts.common.ResultCode;
import com.kewei.parts.module.system.entity.SysUser;
import com.kewei.parts.module.system.mapper.SysUserMapper;
import com.kewei.parts.module.system.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/** 身份只取自认证拦截器，不接受请求正文中的操作者身份。 */
@Component
public class CurrentActor {
    @Autowired private SysUserMapper userMapper;
    @Autowired private RoleService roleService;

    public Long id() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        Object value = request == null ? null : request.getAttribute("userId");
        if (!(value instanceof Long)) throw new BusinessException(ResultCode.UNAUTHORIZED);
        return (Long) value;
    }

    public SysUser user() {
        SysUser user = userMapper.selectById(id());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || Integer.valueOf(1).equals(user.getDeleted()))
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        return user;
    }

    public boolean isAdmin() {
        SysUser user = user();
        return "regulator".equals(user.getUserType()) || roleService.hasRole(user.getId(), "admin");
    }

    public void requireRoles(String... roles) {
        Long userId = id();
        if (isAdmin()) return;
        for (String role : roles) if (roleService.hasRole(userId, role)) return;
        throw new BusinessException(ResultCode.FORBIDDEN);
    }

    public void requireSelfOrAdmin(Long owner) {
        if (!Objects.equals(id(), owner) && !isAdmin()) throw new BusinessException(ResultCode.FORBIDDEN);
    }
}
