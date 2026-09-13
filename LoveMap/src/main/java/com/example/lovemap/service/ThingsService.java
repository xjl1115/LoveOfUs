package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.ThingsAchieveDTO;
import com.example.lovemap.model.dto.ThingsPhotoUploadDTO;
import com.example.lovemap.model.vo.ThingsListVO;
import com.example.lovemap.model.vo.ThingsPhotoVO;
import com.example.lovemap.model.vo.ThingsStatsVO;

import java.util.List;

/**
 * 情侣必做 100 件事服务接口
 */
public interface ThingsService {

    /**
     * 获取情侣共享的 100 件事列表（含完成状态、照片数）
     *
     * @param userId 当前用户
     * @return 列表 VO
     */
    Result<List<ThingsListVO>> listThings(Integer userId);

    /**
     * 进度统计
     */
    Result<ThingsStatsVO> getStats(Integer userId);

    /**
     * 标记完成 / 取消完成（DTO 里 thingId 必填）
     * 通过 note 是否为空判断动作：
     *   note == null 且 photoIds == null → 取消
     *   否则 → 标记完成
     */
    Result<Void> achieve(Integer userId, ThingsAchieveDTO dto);

    /**
     * 获取事项关联的图片列表（按 group 权限校验）
     */
    Result<List<ThingsPhotoVO>> listPhotos(Integer userId, Long thingId);

    /**
     * 上传一张图片到事项（URL 由前端先传到 OSS，再把 URL 提交给本接口）
     */
    Result<ThingsPhotoVO> uploadPhoto(Integer userId, ThingsPhotoUploadDTO dto);
}