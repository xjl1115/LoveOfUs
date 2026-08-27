package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.MoodLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MoodLogMapper {

    int insert(MoodLog moodLog);

    List<MoodLog> selectByGroupAndDate(@Param("groupId") Long groupId, @Param("logDate") LocalDate logDate);

    List<MoodLog> selectByGroupInRange(@Param("groupId") Long groupId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    int countByGroupInRange(@Param("groupId") Long groupId,
                             @Param("startDate") LocalDate startDate,
                             @Param("endDate") LocalDate endDate);
}