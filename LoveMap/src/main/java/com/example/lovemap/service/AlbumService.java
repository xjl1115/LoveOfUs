package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AlbumCreateDTO;
import com.example.lovemap.model.dto.AlbumUpdateDTO;
import com.example.lovemap.model.vo.AlbumDetailVO;
import com.example.lovemap.model.vo.AlbumVO;

import java.util.List;

/**
 * 相册服务接口
 */
public interface AlbumService {

    /**
     * 创建相册
     */
    Result<AlbumVO> createAlbum(Integer userId, AlbumCreateDTO dto);

    /**
     * 获取相册列表
     */
    Result<List<AlbumVO>> listAlbums(Integer userId);

    /**
     * 获取相册详情（含照片列表，分页）
     */
    Result<AlbumDetailVO> getAlbumDetail(Integer userId, Long albumId, Integer page, Integer size);

    /**
     * 更新相册信息
     */
    Result<AlbumVO> updateAlbum(Integer userId, Long albumId, AlbumUpdateDTO dto);

    /**
     * 删除相册
     */
    Result<Void> deleteAlbum(Integer userId, Long albumId);

    /**
     * 批量添加照片到相册
     */
    Result<Void> addPhotosToAlbum(Integer userId, Long albumId, List<Long> photoIds);

    /**
     * 从相册移除照片
     */
    Result<Void> removePhotoFromAlbum(Integer userId, Long albumId, Long photoId);

    /**
     * 清除相册详情缓存
     */
    void clearAlbumDetailCache(Long albumId);

    /**
     * 清除相册详情缓存及相册列表缓存
     */
    void clearAlbumDetailCache(Long albumId, Integer userId);
}
