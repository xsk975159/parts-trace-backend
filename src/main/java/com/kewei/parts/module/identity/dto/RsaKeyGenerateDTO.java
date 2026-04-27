package com.kewei.parts.module.identity.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * RSA密钥生成DTO
 */
@Data
public class RsaKeyGenerateDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 密钥名称
     */
    @NotBlank(message = "密钥名称不能为空")
    private String keyName;

    /**
     * 是否设为默认密钥"-" 1-"
     */
    private Integer isDefault;

    /**
     * 过期天数（可选，默认365天）
     */
    private Integer expireDays;
}






