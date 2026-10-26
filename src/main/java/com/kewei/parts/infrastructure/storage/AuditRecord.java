package com.kewei.parts.infrastructure.storage;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;
@Data
@TableName("operation_audit")
public class AuditRecord {
    @TableId(type=IdType.AUTO) private Long id;
    private String action;
    private Long subjectId;
    private String dataHash;
    private String objectCid;
    private String chainTxId;
    private Date createTime;
}
