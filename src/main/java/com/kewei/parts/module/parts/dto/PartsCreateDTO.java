package com.kewei.parts.module.parts.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 零部件创建DTO
 */
@Data
public class PartsCreateDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "零部件编码不能为")
    private String partsCode;
    
    @NotBlank(message = "零部件名称不能为")
    private String partsName;
    
    @NotBlank(message = "批次号不能为")
    private String batchNumber;
    
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;
    
    private String specification;
    
    private String unit;
    
    @NotNull(message = "数量不能为空")
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
}





