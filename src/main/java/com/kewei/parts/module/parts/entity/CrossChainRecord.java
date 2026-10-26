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
 * 跨链存证记录：主监管链账本的数据库镜像。
 * 对应合约方法 {@code receive_cross_anchor}——主链只汇聚各业务子链的摘要（源链 + 源交易 + 哈希 + CID）。
 */
@Data
@TableName("cross_chain_record")
public class CrossChainRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 源业务子链编码 */
    private String sourceChainId;

    /** 源链交易哈希 */
    private String sourceTxHash;

    /** 源链区块高度 */
    private Long sourceBlockHeight;

    /** 业务类型：trace_event / vc / did_document_unit */
    private String businessType;
    private Long businessId;
    private Integer attempts;
    private String lastError;
    private Date nextRetryAt;

    /** 零部件 DID */
    private String partsDid;

    /** 数据哈希（摘要） */
    private String dataHash;

    /** 对象全文 CID（IPFS/DB 模拟） */
    private String objectCid;

    /** 目标链（固定主监管链） */
    private String targetChainId;

    /** 主链上的交易哈希 */
    private String targetTxHash;

    /** 主链区块高度 */
    private Long targetBlockHeight;

    /** 1=已中继 */
    private Integer status;

    private Date relayTime;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
