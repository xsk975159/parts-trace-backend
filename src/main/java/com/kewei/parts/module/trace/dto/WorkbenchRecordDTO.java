package com.kewei.parts.module.trace.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class WorkbenchRecordDTO {

    @NotNull(message = "零部件ID不能为空")
    private Long partsId;

    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    @NotBlank(message = "事件名称不能为空")
    private String eventName;

    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;

    @NotBlank(message = "操作人不能为空")
    private String operatorName;

    private String organization;
    private String eventLocation;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date eventTime;
    private String evidenceUrls;
    private Boolean issueVc;

    // production
    private String productionLine;
    private String productionBatch;

    // quality
    private String inspectionType;
    private String inspectionResult;

    // logistics
    private String logisticsType;
    private String trackingNumber;
}
