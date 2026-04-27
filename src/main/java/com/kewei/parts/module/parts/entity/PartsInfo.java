package com.kewei.parts.module.parts.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 零部件基础信息实体"
 */
@Data
@TableName("parts_info")
public class PartsInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String partsCode;
    
    private String partsName;
    
    private String batchNumber;
    
    private Long categoryId;
    
    private String specification;
    
    private String unit;
    
    private BigDecimal quantity;
    
    private BigDecimal weight;
    
    private String material;
    
    private String manufacturer;
    
    private Long manufacturerId;
    
    private Date productionDate;
    
    private Date expiryDate;
    
    private String qualityStandard;
    
    private String certification;
    
    private String imageUrl;
    
    private String description;
    
    private Integer status;
    
    private Long creatorId;
    
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    
    @TableLogic
    private Integer deleted;
}





