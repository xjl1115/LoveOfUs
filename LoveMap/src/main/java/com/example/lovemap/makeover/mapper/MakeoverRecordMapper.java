package com.example.lovemap.makeover.mapper;

import com.example.lovemap.model.entity.MakeoverRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 化妆建议记录 Mapper
 */
@Mapper
public interface MakeoverRecordMapper {

    /**
     * 插入记录
     */
    int insert(MakeoverRecord record);

    /**
     * 根据主键查询
     */
    MakeoverRecord selectById(@Param("id") Long id);

    /**
     * 更新状态
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Byte status,
                     @Param("errorMessage") String errorMessage,
                     @Param("costMs") Long costMs);

    /**
     * 落库 AI 文本结果（脸型特征 + 妆造建议 + 改造总结）
     */
    int updateAiText(@Param("id") Long id,
                     @Param("faceFeatures") String faceFeatures,
                     @Param("suggestions") String suggestions,
                     @Param("summary") String summary);

    /**
     * 落库改造图结果
     */
    int updateAfterImage(@Param("id") Long id,
                         @Param("afterUrl") String afterUrl,
                         @Param("afterKey") String afterKey);

    /**
     * 软删除
     */
    int softDelete(@Param("id") Long id,
                   @Param("userId") Integer userId);

    /**
     * 分页查询（按用户）
     */
    List<MakeoverRecord> selectPageByUser(@Param("userId") Integer userId,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    /**
     * 统计数量
     */
    long countByUser(@Param("userId") Integer userId);

    /**
     * 统计某用户在一个时间区间内提交的次数（含已软删记录，避免删除后刷回额度）
     *
     * @param userId 用户 ID
     * @param start  区间起（含）
     * @param end    区间止（不含）
     */
    long countMonthlyByUser(@Param("userId") Integer userId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);
}
