package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.AnniversaryReminder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnniversaryReminderMapper {

    int insert(AnniversaryReminder reminder);

    List<AnniversaryReminder> selectByAnniversary(@Param("anniversaryId") Long anniversaryId);

    AnniversaryReminder selectByGroupAndId(@Param("id") Long id, @Param("groupId") Long groupId);

    int deleteByAnniversary(@Param("anniversaryId") Long anniversaryId);
}