package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.PhotoAlbum;
import com.example.lovemap.model.vo.PhotoDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 照片-相册关联 Mapper
 */
@Mapper
public interface PhotoAlbumMapper {

    /**
     * 批量添加照片到相册
     */
    int batchInsert(@Param("albumId") Long albumId, @Param("photoIds") List<Long> photoIds);

    /**
     * 从相册移除单张照片
     */
    int deleteByAlbumIdAndPhotoId(@Param("albumId") Long albumId, @Param("photoId") Long photoId);

    /**
     * 查询照片是否已在相册中
     */
    int countByAlbumIdAndPhotoId(@Param("albumId") Long albumId, @Param("photoId") Long photoId);

    /**
     * 删除照片的所有相册关联（物理删除照片时使用）
     */
    int deleteByPhotoId(@Param("photoId") Long photoId);

    /**
     * 查询照片所属的所有相册ID
     */
    List<Long> selectAlbumIdsByPhotoId(@Param("photoId") Long photoId);

    /**
     * 查询照片所属的所有相册（含ID和名称）
     */
    List<PhotoDetailVO.AlbumInfo> selectAlbumsByPhotoId(@Param("photoId") Long photoId);

    /**
     * 查询相册下所有照片ID
     */
    List<Long> selectPhotoIdsByAlbumId(@Param("albumId") Long albumId);
}
