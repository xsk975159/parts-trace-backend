package com.kewei.parts.module.parts.vo;

import lombok.Data;

import java.util.Date;

@Data
public class PartsDidVO {

    private Long id;
    private Long partsId;
    private Long unitId;
    private String unitCode;
    private String partsCode;
    private String partsName;
    private String batchNumber;

    private String did;
    private Integer bindStatus;
    private Integer didStatus;

    private String docCid;
    private Long blockHeight;
    private String chainTxId;

    private String bindType;
    private String bindCode;

    private Date bindTime;
    private Date createTime;
}
