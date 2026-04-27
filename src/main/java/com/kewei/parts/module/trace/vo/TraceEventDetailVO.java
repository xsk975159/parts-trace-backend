package com.kewei.parts.module.trace.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 溯源事件详情VO
 */
@Data
public class TraceEventDetailVO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private Long id;
    
    private String eventCode;
    
    private Long partsId;
    
    private String partsName;
    
    private String batchNumber;
    
    private String eventType;
    
    private String eventTypeName;
    
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
    
    private String blockchainTxId;
    
    private Integer status;
    
    private String statusName;
    
    private String remark;
    
    private Date createTime;
    
    // 事件详细信息（根据类型不同）
    private Object detailInfo;
}





