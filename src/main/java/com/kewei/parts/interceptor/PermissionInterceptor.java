package com.kewei.parts.interceptor;

import com.alibaba.fastjson.JSON;
import com.kewei.parts.common.Result;
import com.kewei.parts.common.ResultCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 权限拦截"
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @org.springframework.beans.factory.annotation.Autowired
    private com.kewei.parts.security.CurrentActor actor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跨域预检请求直接放行
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        boolean write = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
        try {
            if (uri.startsWith("/api/admin/") || uri.matches("/api/user/(list|create|update|delete/.*|username/.*)")
                    || (write && (uri.startsWith("/api/role/") || uri.startsWith("/api/permission/")))) {
                actor.requireRoles("admin");
            }
            if (uri.startsWith("/api/role/user/")) {
                String owner = uri.substring("/api/role/user/".length());
                actor.requireSelfOrAdmin(Long.valueOf(owner));
            }
            if (write && uri.startsWith("/api/anomaly/")) actor.requireRoles("quality");
            if (write && uri.startsWith("/api/parts/category")) actor.requireRoles("admin");
            if (write && !uri.equals("/api/parts/list") && uri.startsWith("/api/parts/") && !uri.contains("/did/") && !uri.contains("/units/") && !uri.contains("/unit/"))
                actor.requireRoles("manufacturer");
        } catch (com.kewei.parts.common.BusinessException | NumberFormatException e) {
            writeErrorResponse(response, ResultCode.FORBIDDEN);
            return false;
        }
        return true;
    }

    /**
     * 写入错误响应
     */
    private void writeErrorResponse(HttpServletResponse response, ResultCode resultCode) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.build(resultCode);
        response.getWriter().write(JSON.toJSONString(result));
    }
}





