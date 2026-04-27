package com.kewei.parts.module.trace.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 可验证凭证实体类（VC"
 */
@Data
@TableName("verifiable_credential")
public class VerifiableCredential implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String vcId;
    
    private Long partsId;

    private Long unitId;
    
    private Long eventId;
    
    private String vcType;  // production, quality, transfer, comprehensive
    
    private String issuerDid;
    
    private String issuerName;
    
    private String subjectDid;
    
    private Date issuanceDate;
    
    private Date expirationDate;
    
    private String credentialData;
    
    private String proof;
    
    private Integer status;
    
    private String revocationReason;
    
    private String blockchainTxId;
    
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}





