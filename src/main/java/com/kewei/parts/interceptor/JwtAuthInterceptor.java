package com.kewei.parts.interceptor;

import com.alibaba.fastjson.JSON;
import com.kewei.parts.common.Result;
import com.kewei.parts.common.ResultCode;
import com.kewei.parts.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT认证拦截"
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired private com.kewei.parts.module.system.mapper.SysUserMapper userMapper;
    @Autowired private com.kewei.parts.security.RevokedTokenMapper revokedTokenMapper;

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000)
    public void cleanExpiredRevocations() {
        revokedTokenMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.kewei.parts.security.RevokedToken>()
                .lt(com.kewei.parts.security.RevokedToken::getExpiresAt,new java.util.Date()));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跨域预检请求直接放行
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 从请求头获取Token
        String token = request.getHeader("Authorization");
        
        if (!StringUtils.hasText(token)) {
            writeErrorResponse(response, ResultCode.UNAUTHORIZED);
            return false;
        }

        // 移除Bearer前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 验证Token
        if (!jwtUtil.validateToken(token)) {
            writeErrorResponse(response, ResultCode.TOKEN_INVALID);
            return false;
        }

        // 检查Token是否过期
        if (jwtUtil.isTokenExpired(token)) {
            writeErrorResponse(response, ResultCode.TOKEN_EXPIRED);
            return false;
        }

        com.auth0.jwt.interfaces.DecodedJWT decoded = jwtUtil.parseToken(token);
        if (decoded == null || !"access".equals(decoded.getClaim("tokenType").asString())) {
            writeErrorResponse(response, ResultCode.UNAUTHORIZED); return false;
        }
        com.kewei.parts.module.system.entity.SysUser account = userMapper.selectById(decoded.getClaim("userId").asLong());
        if (account == null || !Integer.valueOf(1).equals(account.getStatus()) || Integer.valueOf(1).equals(account.getDeleted())
                || !jwtUtil.authStamp(account.getPassword()).equals(decoded.getClaim("authStamp").asString())
                || revokedTokenMapper.selectById(decoded.getId()) != null) {
            writeErrorResponse(response, ResultCode.UNAUTHORIZED); return false;
        }
        // 从Token中获取用户信息并设置到request"
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        String userType = account.getUserType();

        request.setAttribute("userId", userId);
        request.setAttribute("username", username);
        request.setAttribute("userType", userType);

        return true;
    }

    /**
     * 写入错误响应
     */
    private void writeErrorResponse(HttpServletResponse response, ResultCode resultCode) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.build(resultCode);
        response.getWriter().write(JSON.toJSONString(result));
    }
}





