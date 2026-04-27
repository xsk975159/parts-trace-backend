package com.kewei.parts.module.trace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产事件实体
 */
@Data
@TableName("trace_production")
public class TraceProduction implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private Long partsId;

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

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
