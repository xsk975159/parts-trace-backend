package com.kewei.parts.module.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;

@Data
public class WorkbenchBatchActionDTO {
    private String requestId;

    @NotBlank(message = "批次号不能为空")
    private String batchNumber;

    /** production / quality / logistics */
    @NotBlank(message = "动作类型不能为空")
    private String actionType;

    private String eventName;
    private Long operatorId;
    private String operatorName;
    private String organization;
    private String eventLocation;
    @javax.validation.constraints.NotNull(message="事件时间不能为空")
    private Date eventTime;
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

    /** 可选：仅处理这些 parts_info 下的单件；不传或空则整批 */
    private List<Long> partsIds;

    /** 可选：仅处理这些单件；工作台应传当前阶段可处理的 unitId 列表 */
    private List<Long> unitIds;
}
