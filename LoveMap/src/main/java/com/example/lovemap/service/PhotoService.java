package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.PhotoUploadDTO;
import com.example.lovemap.model.vo.PhotoDetailVO;
import com.example.lovemap.model.vo.PhotoUploadVO;
import com.example.lovemap.model.vo.TimelineResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 照片服务接口
 */
public interface PhotoService {

    /**
     * 上传照片（支持批量）
     *
     * @param userId 用户ID
     * @param files  照片文件列表
     * @param dto    照片信息（拍摄日期、地点、描述等）
     * @return 上传结果
     */
    Result<PhotoUploadVO> uploadPhotos(Integer userId, List<MultipartFile> files, PhotoUploadDTO dto);

    /**
     * 获取时间线照片（按月分组）
     *
     * @param userId   用户ID
     * @param page     页码
     * @param size     每页大小
     * @param provence 省份名称过滤（可选）
     * @param city     城市名称过滤（可选）
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 时间线结果
     */
    Result<TimelineResultVO> getTimeline(Integer userId, Integer page, Integer size, String provence, String city, LocalDate startDate, LocalDate endDate);

    /**
     * 删除照片（从 OSS 和数据库中物理删除）
     *
     * @param userId  用户ID
     * @param photoId 照片ID
     * @return 操作结果
     */
    Result<Void> deletePhoto(Integer userId, Long photoId);

    /**
     * 获取照片详情
     *
     * @param userId  用户ID
     * @param photoId 照片ID
     * @return 照片详情
     */
    Result<PhotoDetailVO> getPhotoDetail(Integer userId, Long photoId);
}
