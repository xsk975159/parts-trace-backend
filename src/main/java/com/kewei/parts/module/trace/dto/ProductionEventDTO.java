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

    @NotNull(message = "零部件ID不能为空")
    private Long partsId;
    
    @NotBlank(message = "事件名称不能为空")
    private String eventName;
    
    @NotNull(message = "事件时间不能为空")
    private Date eventTime;
    
    private String eventLocation;
    
    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;
    
    @NotBlank(message = "操作人姓名不能为")
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





