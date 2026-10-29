package com.kewei.parts.infrastructure.confirmation;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.util.Date;

@Data
@TableName("relay_confirmation")
public class RelayConfirmation {
    @TableId(type=IdType.AUTO) private Long id;
    private Long recordId;
    private String committeeVersion;
    private Integer requiredVotes;
    private Integer validVotes;
    private String state;
    @JsonIgnore private String proofJson;
    private String lastError;
    private Date updatedAt;
}
