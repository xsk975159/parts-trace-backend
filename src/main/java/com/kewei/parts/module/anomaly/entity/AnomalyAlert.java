package com.kewei.parts.module.anomaly.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 异常预警实体"
 */
@Data
@TableName("anomaly_alert")
public class AnomalyAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 预警类型 */
    private String alertType;

    /** 预警级别：warning-警告, error-严重 */
    private String alertLevel;

    private String title;

    private Long partsId;

    private String partsCode;

    private Long ruleId;

    private String ruleName;

    private String description;

    private Date alertTime;

    /** 处理状态：0-未处" 1-已处"*/
    private Integer status;

    private Date handleTime;

    private String handler;

    private String handleResult;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}





