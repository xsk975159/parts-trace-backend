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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跨域预检请求直接放行
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String userType = (String) request.getAttribute("userType");
        String requestUri = request.getRequestURI();

        // 管理员接口权限检"
        if (requestUri.startsWith("/api/admin/")) {
            if (!"regulator".equals(userType)) {
                writeErrorResponse(response, ResultCode.FORBIDDEN);
                return false;
            }
        }

        // 供应链用户特殊权限检"
        if (requestUri.contains("/supply-chain/")) {
            if (!"supply_chain".equals(userType) && !"regulator".equals(userType)) {
                writeErrorResponse(response, ResultCode.FORBIDDEN);
                return false;
            }
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





