package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.QuotaLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuotaLogMapper {

    @Insert("INSERT INTO quota_log (user_id, quota_type, quota_source, used_at)"
            + " VALUES (#{userId}, #{quotaType}, #{quotaSource}, #{usedAt})")
    int insert(QuotaLog log);

    /**
     * 统计某周期内指定来源的消耗次数
     *
     * @param quotaSource 1=周期基础额度，2=次卡入账
     */
    @Select("SELECT COUNT(*) FROM quota_log WHERE user_id = #{userId} AND quota_type = #{quotaType}"
            + " AND quota_source = #{quotaSource} AND used_at >= #{start} AND used_at < #{end}")
    long countBySourceInPeriod(@Param("userId") Long userId,
                               @Param("quotaType") Integer quotaType,
                               @Param("quotaSource") Integer quotaSource,
                               @Param("start") java.time.LocalDateTime start,
                               @Param("end") java.time.LocalDateTime end);

    /**
     * 统计某来源的历史累计消耗次数（次卡入账不过期，按用户全量统计）
     */
    @Select("SELECT COUNT(*) FROM quota_log WHERE user_id = #{userId} AND quota_type = #{quotaType}"
            + " AND quota_source = #{quotaSource}")
    long countBySource(@Param("userId") Long userId,
                       @Param("quotaType") Integer quotaType,
                       @Param("quotaSource") Integer quotaSource);
}
