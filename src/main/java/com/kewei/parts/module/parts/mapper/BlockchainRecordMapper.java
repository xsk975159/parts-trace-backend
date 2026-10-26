package com.kewei.parts.module.parts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kewei.parts.module.parts.entity.BlockchainRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 区块链记录Mapper
 */
@Mapper
public interface BlockchainRecordMapper extends BaseMapper<BlockchainRecord> {

    /** 指定链当前最大区块高度（用于模拟该链单调递增高度） */
    @Select("SELECT COALESCE(MAX(block_height), 0) FROM blockchain_record WHERE chain_id = #{chainId}")
    Long selectMaxBlockHeight(@Param("chainId") String chainId);
}
