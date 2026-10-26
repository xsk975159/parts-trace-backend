package com.kewei.parts.module.parts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kewei.parts.module.parts.entity.PartsInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 零部件信息Mapper
 */
@Mapper
public interface PartsInfoMapper extends BaseMapper<PartsInfo> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM parts_info WHERE id=#{id} AND deleted=0 FOR UPDATE")
    PartsInfo lockById(@org.apache.ibatis.annotations.Param("id") Long id);
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(c.category_name,'未分类') name, COUNT(*) count FROM parts_info p LEFT JOIN parts_category c ON c.id=p.category_id WHERE p.deleted=0 GROUP BY c.id,c.category_name")
    java.util.List<java.util.Map<String,Object>> categoryStatistics();
}





