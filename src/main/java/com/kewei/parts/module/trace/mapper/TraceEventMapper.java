package com.kewei.parts.module.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kewei.parts.module.trace.entity.TraceEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 溯源事件Mapper
 */
@Mapper
public interface TraceEventMapper extends BaseMapper<TraceEvent> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM trace_event WHERE id=#{id} AND deleted=0 FOR UPDATE")
    TraceEvent lockById(@org.apache.ibatis.annotations.Param("id") Long id);
    @org.apache.ibatis.annotations.Select("SELECT event_type eventType, COUNT(*) count FROM trace_event WHERE deleted=0 GROUP BY event_type")
    java.util.List<java.util.Map<String,Object>> eventDistribution();
    @org.apache.ibatis.annotations.Select("SELECT DATE_FORMAT(event_time,'%Y-%m-%d') day, event_type eventType, COUNT(*) count FROM trace_event WHERE deleted=0 AND event_time>=#{since} GROUP BY DATE_FORMAT(event_time,'%Y-%m-%d'),event_type ORDER BY day")
    java.util.List<java.util.Map<String,Object>> eventTrend(@org.apache.ibatis.annotations.Param("since") java.util.Date since);
}





