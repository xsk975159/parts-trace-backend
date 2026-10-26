package com.kewei.parts.module.trace.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kewei.parts.infrastructure.storage.ChainTask;
import org.apache.ibatis.annotations.*;
public interface ChainTaskMapper extends BaseMapper<ChainTask> {
    @Update("UPDATE chain_task SET status=3, attempts=attempts+1, next_retry_at=DATE_ADD(NOW(), INTERVAL 5 MINUTE) WHERE id=#{id} AND status IN (0,-1,3) AND (next_retry_at IS NULL OR next_retry_at<=NOW()) AND attempts<10")
    int claim(@Param("id") Long id);
}
