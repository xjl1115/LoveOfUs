package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.ThingsUrl;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 事项图片URL Mapper
 */
@Mapper
public interface ThingsUrlMapper {

    /**
     * 插入图片URL
     */
    int insert(ThingsUrl thingsUrl);

    /**
     * 根据 ID 查询
     */
    ThingsUrl selectById(@Param("id") Long id);

    /**
     * 根据 ID 删除
     */
    int deleteById(@Param("id") Long id);
}