package com.kewei.parts.security;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;
@Data
@TableName("revoked_token")
public class RevokedToken {
    @TableId(type=com.baomidou.mybatisplus.annotation.IdType.INPUT) private String id;
    private Date expiresAt;
}
