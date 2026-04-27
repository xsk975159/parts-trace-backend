package com.kewei.parts.module.parts.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 零部件DID实体
 */
@Data
@TableName("parts_did")
public class PartsDid implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long partsId;

    /** 单件ID（批次拆分后的具体实物件） */
    private Long unitId;

    private String did;

    private String didDocument;

    private String publicKey;

    /** 是否绑定物理标识：0否 1是 */
    private Integer bindStatus;

    private Date bindTime;

    /** DID生命周期状态：0未激活 1已激活 2已冻结 3已归档 */
    private Integer didStatus;

    /** DID文档摘要哈希 */
    private String docHash;

    /** DID文档CID（数据库模拟IPFS） */
    private String docCid;

    /** 链上交易ID（数据库模拟上链） */
    private String chainTxId;

    /** 链上区块高度（数据库模拟上链） */
    private Long blockHeight;

    /** 绑定类型：QRCODE / RFID */
    private String bindType;

    /** 绑定码值 */
    private String bindCode;

    private Date unboundTime;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
