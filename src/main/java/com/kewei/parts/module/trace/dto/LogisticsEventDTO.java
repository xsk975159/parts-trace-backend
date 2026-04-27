package com.kewei.parts.module.trace.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
 * 物流事件DTO
 */
@Data
public class LogisticsEventDTO implements Serializable {
    
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
    
    // 物流事件特有字段
    @NotBlank(message = "物流类型不能为空")
    private String logisticsType;
    
    private String logisticsCompany;
    
    private String trackingNumber;
    
    private String sender;
    
    private String senderAddress;
    
    private String receiver;
    
    private String receiverAddress;
    
    private Date departureTime;
    
    private Date arrivalTime;
    
    private String currentLocation;
    
    private String transportMode;
    
    private String vehicleNumber;
    
    private String driver;
    
    private String driverPhone;
    
    private String temperatureRecord;
    
    private String humidityRecord;
    
    private String gpsTrack;
    
    private String packageStatus;
    
    private String signatureUrl;
}





