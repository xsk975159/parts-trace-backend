package com.kewei.parts.module.trace.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产事件DTO
 */
@Data
public class ProductionEventDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private String requestId;

    @NotNull(message = "零部件ID不能为空")
    private Long partsId;

    /** 单件ID（批次工作台按单件处理时传入） */
    private Long unitId;
    
    @NotBlank(message = "事件名称不能为空")
    private String eventName;
    
    @NotNull(message = "事件时间不能为空")
    private Date eventTime;
    
    private String eventLocation;
    
    private Long operatorId;
    
    private String operatorName;
    
    private String organization;
    
    private String evidenceUrls;
    
    private String remark;
    
    // 生产事件特有字段
    private String productionLine;
    
    private String productionBatch;
    
    private BigDecimal productionQuantity;
    
    private String rawMaterials;
    
    private String productionProcess;
    
    private String equipmentInfo;
    
    private BigDecimal temperature;
    
    private BigDecimal humidity;
    
    private BigDecimal pressure;
    
    private String qualityLevel;
    
    private String inspector;
    
    private String inspectionResult;
}





