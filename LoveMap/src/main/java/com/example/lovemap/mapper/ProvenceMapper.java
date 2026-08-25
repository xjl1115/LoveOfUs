package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.Provence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 省份 Mapper
 */
@Mapper
public interface ProvenceMapper {

    /**
     * 根据省份名称查询省份ID
     *
     * @param provenceName 省份名称（如"湖北"）
     * @return 省份ID，不存在返回 null
     */
    @Select("SELECT id FROM provence WHERE provence = #{provenceName}")
    Integer selectIdByName(@Param("provenceName") String provenceName);

    /**
     * 根据省份ID查询省份名称
     *
     * @param id 省份ID
     * @return 省份名称，不存在返回 null
     */
    @Select("SELECT provence FROM provence WHERE id = #{id}")
    String selectNameById(@Param("id") Integer id);
}