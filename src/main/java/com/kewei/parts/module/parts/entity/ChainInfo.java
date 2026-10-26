package com.kewei.parts.module.parts.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 链注册表：主监管链与三条业务子链的显示信息。
 * 实际SDK平台由blockchain.networks配置决定，接口返回时同步该平台类型。
 */
@Data
@TableName("chain_info")
public class ChainInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 链编码：main-regulator / sub-production / sub-quality / sub-logistics */
    private String chainCode;

    /** 链显示名：主监管链 / 生产子链 / 质检子链 / 物流子链 */
    private String chainName;

    /** 链类型：CHAINMAKER / FISCO_BCOS */
    private String chainType;

    /** 链角色：REGULATOR（主监管链）/ BUSINESS（业务子链） */
    private String chainRole;

    private String description;

    /** 1=启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
