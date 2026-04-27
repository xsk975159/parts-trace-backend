package com.kewei.parts.module.identity.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户DID身份绑定实体
 */
@Data
@TableName("sys_user_did")
public class SysUserDid implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * DID标识
     */
    private String did;

    /**
     * DID文档（JSON格式）
     */
    private String didDocument;

    /**
     * 公钥
     */
    private String publicKey;

    /**
     * 绑定状态：0-未绑定，1-已绑定
     */
    private Integer bindStatus;

    /**
     * 绑定时间
     */
    private Date bindTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
