package com.kewei.parts.module.system.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户信息VO
 */
@Data
public class UserInfoVO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户"
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机"
     */
    private String phone;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 账户状"
     */
    private Integer status;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 最后登录时"
     */
    private Date lastLoginTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * DID标识"
     */
    private String did;

    /**
     * DID绑定状"
     */
    private Integer didBindStatus;
}





