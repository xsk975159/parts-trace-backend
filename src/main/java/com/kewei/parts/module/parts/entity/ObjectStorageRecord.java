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
 * 对象存储记录（数据库模拟IPFS）
 */
@Data
@TableName("object_storage_record")
public class ObjectStorageRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String cid;

    private String businessType;

    private Long businessId;

    private String contentHash;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
