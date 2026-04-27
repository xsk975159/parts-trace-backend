package com.kewei.parts.module.system.vo;

import lombok.Data;
import java.io.Serializable;

/**
 * 用户登录响应VO
 */
@Data
public class UserLoginVO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户"
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 访问Token
     */
    private String accessToken;

    /**
     * 刷新Token
     */
    private String refreshToken;

    /**
     * Token过期时间（毫秒）
     */
    private Long expiresIn;
}





