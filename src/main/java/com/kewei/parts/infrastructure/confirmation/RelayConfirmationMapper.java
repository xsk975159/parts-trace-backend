package com.kewei.parts.infrastructure.confirmation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RelayConfirmationMapper extends BaseMapper<RelayConfirmation> {
    @Select("SELECT * FROM relay_confirmation WHERE record_id=#{id}")
    RelayConfirmation findRecord(@Param("id") Long id);
}
