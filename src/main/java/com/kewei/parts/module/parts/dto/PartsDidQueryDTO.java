package com.kewei.parts.module.parts.dto;

import lombok.Data;

@Data
public class PartsDidQueryDTO {

    private Long partsId;

    private String batchNumber;

    private String did;

    private Integer didStatus;

    /** 默认只查询单件DID（unit_id 非空） */
    private Boolean onlyUnit;
}
