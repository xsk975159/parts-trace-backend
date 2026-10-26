package com.kewei.parts.infrastructure.storage;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;
@Data
@TableName("chain_task")
public class ChainTask {
    @TableId(type=IdType.AUTO) private Long id;
    private String taskKey;
    private String action;
    private String payload;
    private Integer status;
    private Integer attempts;
    private String lastError;
    private String txId;
    private Date nextRetryAt;
    private Date createTime;
}
