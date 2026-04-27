package com.kewei.parts.module.anomaly.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 异常检测规则实体类
 */
@Data
@TableName("anomaly_rule")
public class AnomalyRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String ruleName;

    private String ruleCode;

    /** 规则类型：time-时间规则, quality-质量规则, logistics-物流规则 */
    private String ruleType;

    private String description;

    /** 触发条件（JSON字符串） */
    private String condition;

    /** 处理动作（JSON字符串） */
    private String action;

    /** 状态：0-禁用, 1-启用 */
    private Integer status;

    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}





