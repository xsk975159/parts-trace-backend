package com.kewei.parts.module.parts.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 零部件更新DTO
 */
@Data
public class PartsUpdateDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private Long id;

    private String partsName;
    
    private Long categoryId;
    
    private String specification;
    
    private String unit;
    
    private BigDecimal quantity;
    
    private BigDecimal weight;
    
    private String material;
    
    private String manufacturer;
    
    private Date productionDate;
    
    private Date expiryDate;
    
    private String qualityStandard;
    
    private String certification;
    
    private String imageUrl;
    
    private String description;
    
    private Integer status;
}





