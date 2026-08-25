package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.Album;
import com.example.lovemap.model.entity.Photo;
import com.example.lovemap.model.vo.AlbumDetailVO;
import com.example.lovemap.model.vo.AlbumVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 相册 Mapper
 */
@Mapper
public interface AlbumMapper {

    /**
     * 创建相册
     */
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Album album);

    /**
     * 根据ID查询相册
     */
    Album selectById(@Param("id") Long id);

    /**
     * 查询相册列表（根据 group_id 或 user_id）
     */
    List<AlbumVO> selectListByUser(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * 根据 group_id 查询最新照片的 storage_path（作为封面）
     */
    String selectLatestPhotoPathByGroupId(@Param("groupId") Long groupId);

    /**
     * 根据 user_id 查询最新照片的 storage_path（作为封面，未绑定时使用）
     */
    String selectLatestPhotoPathByUserId(@Param("userId") Long userId);

    /**
     * 查询相册基本信息（用于详情页）
     */
    AlbumVO selectAlbumById(@Param("id") Long id);

    /**
     * 分页查询相册内的照片列表（关联 user 表获取昵称）
     */
    List<AlbumDetailVO.PhotoInAlbumVO> selectPhotosByAlbumId(
            @Param("albumId") Long albumId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 统计相册内照片总数
     */
    Long countPhotosByAlbumId(@Param("albumId") Long albumId);

    /**
     * 更新相册名称和描述
     */
    int updateNameAndDescription(@Param("id") Long id, @Param("name") String name, @Param("description") String description);

    /**
     * 删除相册
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除相册下所有照片关联
     */
    int deletePhotoAlbumsByAlbumId(@Param("albumId") Long albumId);

    /**
     * 查询相册中仅属于该相册的独占照片（用于物理删除时连带删除）
     */
    List<Photo> selectOrphanedPhotosByAlbumId(@Param("albumId") Long albumId);

    /**
     * 批量物理删除照片
     */
    int deletePhotosByIds(@Param("ids") List<Long> ids);

    /**
     * 查询相册中所有照片ID
     */
    List<Long> selectPhotoIdsByAlbumId(@Param("albumId") Long albumId);

    /**
     * 查询用户/群组下所有相册的ID（仅ID轻量查询，用于缓存清理）
     */
    List<Long> selectAlbumIdsByGroupOrUser(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
