package com.kewei.parts.module.parts.vo;

import lombok.Data;

@Data
public class DidGenerateTaskVO {

    private String taskId;

    private Long partsId;

    /** 0排队 1处理中 2成功 3失败 */
    private Integer status;

    private Integer progress;

    private String resultDid;

    private String errorMsg;
}
