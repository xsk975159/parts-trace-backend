package com.kewei.parts.module.parts.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 零部件查询DTO
 */
@Data
public class PartsQueryDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private String partsName;
    
    private String batchNumber;
    
    private Long categoryId;
    
    private String manufacturer;
    
    private Integer status;
    
    private Integer pageNum = 1;
    
    private Integer pageSize = 10;
}





