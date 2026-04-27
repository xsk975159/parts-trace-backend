package com.kewei.parts.module.parts.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class PartsDidBindDTO {

    @NotBlank(message = "绑定类型不能为空")
    private String bindType;

    @NotBlank(message = "绑定码不能为空")
    private String bindCode;

    private String remark;
}
