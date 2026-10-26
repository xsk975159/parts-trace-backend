package com.kewei.parts.module.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kewei.parts.module.trace.entity.TraceQuality;
import org.apache.ibatis.annotations.Mapper;

/**
 * 质检事件Mapper
 */
@Mapper
public interface TraceQualityMapper extends BaseMapper<TraceQuality> {
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) total, SUM(CASE WHEN q.inspection_result IN ('合格','pass','qualified') THEN 1 ELSE 0 END) passed FROM trace_quality q JOIN trace_event e ON e.id=q.event_id WHERE e.deleted=0 AND e.status=1 AND e.event_time>=#{since}")
    java.util.Map<String,Object> monthlyPassRate(@org.apache.ibatis.annotations.Param("since") java.util.Date since);
}





