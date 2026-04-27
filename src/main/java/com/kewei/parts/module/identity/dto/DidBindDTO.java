package com.kewei.parts.module.identity.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * DID绑定DTO
 */
@Data
public class DidBindDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 公钥（可选，如果不提供则自动生成RSA密钥对）
     */
    private String publicKey;

    /**
     * 私钥（可选，如果不提供则自动生成RSA密钥对）
     */
    private String privateKey;
}






