package com.kewei.parts.config;

import com.kewei.parts.interceptor.JwtAuthInterceptor;
import com.kewei.parts.interceptor.PermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Autowired
    private PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT认证拦截"
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/register",           // 注册接口
                        "/api/login",              // 登录接口
                        "/swagger-ui.html",        // Swagger UI
                        "/swagger-ui/**",          // Swagger UI资源
                        "/v3/api-docs/**",         // API文档
                        "/swagger-resources/**",   // Swagger资源
                        "/webjars/**",             // WebJars资源
                        "/error"                   // 错误页面
                )
                .order(1);

        // 权限拦截"
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/register",
                        "/api/login"
                )
                .order(2);
    }
}





