package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.Group;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 情侣关系表 Mapper
 */
@Mapper
public interface GroupMapper {

    /**
     * 新增情侣关系记录
     *
     * @param group 情侣关系
     * @return 影响行数，插入后 group.id 会被自动回填
     */
    int insert(Group group);
}