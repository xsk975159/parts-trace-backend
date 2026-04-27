package com.kewei.parts.module.trace.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 质检事件DTO
 */
@Data
public class QualityEventDTO implements Serializable {
    
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
    
    // 质检事件特有字段
    @NotBlank(message = "检验类型不能为")
    private String inspectionType;
    
    private String inspectionStandard;
    
    private String inspectionItems;
    
    @NotBlank(message = "检验结果不能为")
    private String inspectionResult;
    
    private BigDecimal qualifiedQuantity;
    
    private BigDecimal unqualifiedQuantity;
    
    private String defectDescription;
    
    @NotBlank(message = "检验员不能为空")
    private String inspector;
    
    private Long inspectorId;
    
    private String inspectionEquipment;
    
    private String testData;
    
    private String reportNumber;
    
    private String reportUrl;
}





