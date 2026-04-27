package com.kewei.parts.module.parts.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 零部件信息VO
 */
@Data
public class PartsInfoVO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private Long id;
    
    private String partsCode;
    
    private String partsName;
    
    private String batchNumber;
    
    private Long categoryId;
    
    private String categoryName;
    
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
    
    private String statusName;
    
    private String did;
    
    private Integer didBindStatus;
    
    private Date createTime;
    
    private Date updateTime;
}





