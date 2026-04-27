package com.kewei.parts.module.parts.vo;

import lombok.Data;

@Data
public class DidValidateVO {

    private String did;

    private Boolean formatValid;

    private Boolean exists;

    private Boolean activated;

    private Boolean bound;

    private Long partsId;

    private String message;
}
