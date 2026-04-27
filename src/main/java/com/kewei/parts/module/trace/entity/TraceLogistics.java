package com.kewei.parts.module.trace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 物流事件实体
 */
@Data
@TableName("trace_logistics")
public class TraceLogistics implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private Long partsId;

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

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
