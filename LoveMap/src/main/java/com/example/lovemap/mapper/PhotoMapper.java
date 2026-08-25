package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.Photo;
import com.example.lovemap.model.vo.PhotoDetailVO;
import com.example.lovemap.model.vo.TimelinePhotoVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 照片 Mapper
 */
@Mapper
public interface PhotoMapper {

    /**
     * 批量插入照片（返回自增ID）
     */
    int batchInsert(List<Photo> photos);

    /**
     * 根据ID查询照片
     */
    Photo selectById(@Param("id") Long id);

    /**
     * 查询情侣组时间线照片列表
     */
    List<TimelinePhotoVO> selectTimelineByGroupId(@Param("groupId") Long groupId,
                                                   @Param("provence") String provence,
                                                   @Param("city") String city,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate,
                                                   @Param("offset") Integer offset,
                                                   @Param("limit") Integer limit);

    /**
     * 查询个人时间线照片列表
     */
    List<TimelinePhotoVO> selectTimelineByUserId(@Param("userId") Long userId,
                                                  @Param("provence") String provence,
                                                  @Param("city") String city,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate,
                                                  @Param("offset") Integer offset,
                                                  @Param("limit") Integer limit);

    /**
     * 统计情侣组照片总数
     */
    Long countTimelineByGroupId(@Param("groupId") Long groupId,
                                 @Param("provence") String provence,
                                 @Param("city") String city,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);

    /**
     * 统计个人照片总数
     */
    Long countTimelineByUserId(@Param("userId") Long userId,
                                @Param("provence") String provence,
                                @Param("city") String city,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    /**
     * 物理删除照片
     */
    int deleteById(@Param("id") Long id);

    /**
     * 查询照片详情（含上传者信息）
     */
    PhotoDetailVO selectPhotoDetailById(@Param("id") Long id);

    /**
     * 查询上一张照片ID（按拍摄时间降序排列）
     */
    Long selectPrevPhotoId(@Param("id") Long id,
                           @Param("userId") Long userId,
                           @Param("takenDate") String takenDate);

    /**
     * 查询下一张照片ID（按拍摄时间降序排列）
     */
    Long selectNextPhotoId(@Param("id") Long id,
                           @Param("userId") Long userId,
                           @Param("takenDate") String takenDate);

    // ========== 导出相关 ==========

    /**
     * 根据ID列表批量查询照片
     */
    List<Photo> selectBatchIds(@Param("ids") List<Long> ids);

    /**
     * 查询情侣组下所有照片ID
     */
    List<Long> selectPhotoIdsByGroupId(@Param("groupId") Long groupId);

    /**
     * 查询用户个人所有照片ID
     */
    List<Long> selectPhotoIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询情侣组下指定日期范围内的照片ID
     */
    List<Long> selectPhotoIdsByGroupIdAndDateRange(@Param("groupId") Long groupId,
                                                    @Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    /**
     * 查询用户个人指定日期范围内的照片ID
     */
    List<Long> selectPhotoIdsByUserIdAndDateRange(@Param("userId") Long userId,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);
}
