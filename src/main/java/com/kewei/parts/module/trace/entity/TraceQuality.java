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
 * 质检事件实体
 */
@Data
@TableName("trace_quality")
public class TraceQuality implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private Long partsId;

    private String inspectionType;

    private String inspectionStandard;

    private String inspectionItems;

    private String inspectionResult;

    private BigDecimal qualifiedQuantity;

    private BigDecimal unqualifiedQuantity;

    private String defectDescription;

    private String inspector;

    private Long inspectorId;

    private String inspectionEquipment;

    private String testData;

    private String reportNumber;

    private String reportUrl;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
