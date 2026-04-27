package com.kewei.parts.common;

import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
public enum ResultCode {

    // 通用状态码
    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),

    // 认证授权相关
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "权限不足，禁止访问"),
    TOKEN_INVALID(4001, "Token无效"),
    TOKEN_EXPIRED(4002, "Token已过期"),

    // 用户相关
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    USER_DISABLED(1003, "用户已被禁用"),
    USERNAME_OR_PASSWORD_ERROR(1004, "用户名或密码错误"),
    PASSWORD_ERROR(1005, "密码错误"),

    // DID相关
    DID_ALREADY_BOUND(2001, "DID已绑定"),
    DID_NOT_BOUND(2002, "DID未绑定"),
    DID_INVALID(2003, "DID无效"),

    // RSA密钥相关
    RSA_KEY_NOT_FOUND(3001, "RSA密钥不存在"),
    RSA_KEY_GENERATE_ERROR(3002, "RSA密钥生成失败"),
    RSA_KEY_EXPIRED(3003, "RSA密钥已过期"),

    // 角色权限相关
    ROLE_NOT_FOUND(5001, "角色不存在"),
    PERMISSION_NOT_FOUND(5002, "权限不存在"),

    // 业务相关
    BUSINESS_ERROR(5000, "业务处理失败");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
