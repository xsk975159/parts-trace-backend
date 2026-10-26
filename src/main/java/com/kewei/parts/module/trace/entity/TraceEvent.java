package com.kewei.parts.module.trace.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 溯源事件实体类（主表"
 */
@Data
@TableName("trace_event")
public class TraceEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String eventCode;
    private String requestId;
    private String requestHash;
    private String auditComment;
    
    private Long partsId;

    private Long unitId;
    
    private String eventType;  // production, quality, logistics
    
    private String eventName;
    
    private Date eventTime;
    
    private String eventLocation;
    
    private Long operatorId;
    
    private String operatorName;
    
    private String organization;
    
    private String eventData;
    
    private String evidenceUrls;
    
    private Long previousEventId;
    
    private Long nextEventId;

    private String partsDid;

    private String batchNumber;

    private Long upstreamEventId;

    private String attachmentCid;

    private String eventHash;

    private Integer retryCount;

    private Integer chainStatus;

    private Integer abnormalMark;

    private String abnormalReason;

    private Integer auditStatus;
    
    private String blockchainTxId;
    
    private Integer status;
    
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    
    @TableLogic
    private Integer deleted;
}





