package com.kewei.parts.module.parts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kewei.parts.module.parts.entity.CrossChainRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 跨链存证记录 Mapper
 */
@Mapper
public interface CrossChainRecordMapper extends BaseMapper<CrossChainRecord> {

    /** 主链当前最大区块高度（用于模拟主链单调递增高度） */
    @Select("SELECT COALESCE(MAX(target_block_height), 0) FROM cross_chain_record")
    Long selectMaxTargetBlockHeight();

    @org.apache.ibatis.annotations.Update("UPDATE cross_chain_record SET status=3, attempts=attempts+1, next_retry_at=DATE_ADD(NOW(), INTERVAL 5 MINUTE) WHERE id=#{id} AND status IN (0,-1,3) AND (next_retry_at IS NULL OR next_retry_at<=NOW()) AND attempts<10")
    int claim(@org.apache.ibatis.annotations.Param("id") Long id);
}
