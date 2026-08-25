package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.ServiceHelper;
import com.example.lovemap.common.constant.AlbumConstant;
import com.example.lovemap.common.constant.PhotoConstant;
import com.example.lovemap.mapper.AlbumMapper;
import com.example.lovemap.mapper.PhotoAlbumMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.dto.AlbumCreateDTO;
import com.example.lovemap.model.dto.AlbumUpdateDTO;
import com.example.lovemap.model.entity.Album;
import com.example.lovemap.model.entity.Photo;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.AlbumDetailVO;
import com.example.lovemap.model.vo.AlbumVO;
import com.example.lovemap.service.AlbumService;
import com.example.lovemap.service.EmailNotificationService;
import com.example.lovemap.service.NotificationService;
import com.example.lovemap.service.UserService;
import com.example.lovemap.utils.AliyunOSSUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumServiceImpl implements AlbumService {

    private final AlbumMapper albumMapper;
    private final PhotoAlbumMapper photoAlbumMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AliyunOSSUtils aliyunOSSUtils;
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;

    /**
     * 列出用户所有相册
     */
    @Override
    public Result<List<AlbumVO>> listAlbums(Integer userId) {
        // 2. 查询数据库（先查用户获取 groupId）
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }

        Long groupId = user.getGroupId();
        // 有 groupId 用 groupId，否则用 userId
        String cacheKey = ServiceHelper.buildCacheKey(AlbumConstant.ALBUM_LIST_PREFIX, groupId, userId.longValue());

        // 1. 尝试从 Redis 缓存获取
        List<AlbumVO> cached = ServiceHelper.getFromCache(redisTemplate, objectMapper, cacheKey, new TypeReference<List<AlbumVO>>() {});
        if (cached != null) {
            return Result.success(cached);
        }

        log.info("查询相册列表, userId: {}, groupId: {}", userId, groupId);
        List<AlbumVO> list = albumMapper.selectListByUser(groupId, userId.longValue());

        // 3. 存入 Redis 缓存
        ServiceHelper.putToCache(redisTemplate, objectMapper, cacheKey, list);
        return Result.success(list);
    }

    /**
     * 创建相册
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<AlbumVO> createAlbum(Integer userId, AlbumCreateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }

        Album album = new Album();
        album.setUserId(user.getId());
        album.setName(dto.getName());
        album.setDescription(dto.getDescription());

        Long groupId = user.getGroupId();
        String coverPath;
        if (groupId != null) {
            album.setGroupId(groupId);
            albumMapper.insert(album);
            coverPath = albumMapper.selectLatestPhotoPathByGroupId(groupId);
        } else {
            albumMapper.insert(album);
            coverPath = albumMapper.selectLatestPhotoPathByUserId(user.getId());
        }

        AlbumVO vo = new AlbumVO();
        vo.setId(album.getId());
        vo.setGroupId(album.getGroupId());
        vo.setName(album.getName());
        vo.setDescription(album.getDescription());
        vo.setCoverPhotoUrl(coverPath);
        vo.setPhotoCount(0);
        vo.setCreatedAt(album.getCreatedAt());
        vo.setUpdatedAt(album.getUpdatedAt());
        // 清除缓存（Redis 故障不应阻断业务：失败仅 warn，下次请求自动重建）
        try {
            clearAlbumListCache(user);
        } catch (Exception e) {
            log.warn("清除相册列表缓存失败, userId: {}", userId, e);
        }
        try {
            clearUserStatsCache(user);
        } catch (Exception e) {
            log.warn("清除用户 stats 缓存失败, userId: {}", userId, e);
        }

        log.info("用户 {} 创建相册成功, albumId: {}, name: {}", userId, album.getId(), dto.getName());
        
        // 事务提交后通知伴侣
        final String albumName = dto.getName();
        final String nickname = user.getNickname() != null ? user.getNickname() : "对方";
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyPartner(userId, nickname + " 创建了新相册《" + albumName + "》");
            }
        });
        
        return Result.success(vo);
    }

    /**
     * 获取相册详情（含照片列表，分页）
     */
    @Override
    public Result<AlbumDetailVO> getAlbumDetail(Integer userId, Long albumId, Integer page, Integer size) {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 20;
        if (size > 100) size = 100;

        String cacheKey = AlbumConstant.ALBUM_DETAIL_PREFIX + albumId + ":" + page + ":" + size;

        // 1. 尝试从 Redis 缓存获取
        AlbumDetailVO cached = ServiceHelper.getFromCache(redisTemplate, objectMapper, cacheKey, AlbumDetailVO.class);
        if (cached != null) {
            return Result.success(cached);
        }

        // 2. 查询数据库
        AlbumVO albumVO = albumMapper.selectAlbumById(albumId);
        if (albumVO == null) {
            return Result.error(ResultCode.NOT_FOUND, "相册不存在");
        }

        int offset = (page - 1) * size;
        List<AlbumDetailVO.PhotoInAlbumVO> photos = albumMapper.selectPhotosByAlbumId(albumId, offset, size);
        Long total = albumMapper.countPhotosByAlbumId(albumId);
        int totalPages = (int) Math.ceil((double) total / size);

        // 3. 构建返回数据
        AlbumDetailVO detail = new AlbumDetailVO();
        detail.setId(albumVO.getId());
        detail.setName(albumVO.getName());
        detail.setDescription(albumVO.getDescription());
        detail.setCoverPhotoUrl(albumVO.getCoverPhotoUrl());
        detail.setPhotoCount(albumVO.getPhotoCount());
        detail.setCreatedAt(albumVO.getCreatedAt());
        detail.setUpdatedAt(albumVO.getUpdatedAt());
        detail.setPhotos(photos);

        AlbumDetailVO.PageVO pageVO = new AlbumDetailVO.PageVO();
        pageVO.setCurrent(page);
        pageVO.setSize(size);
        pageVO.setTotal(total);
        pageVO.setPages(totalPages);
        detail.setPage(pageVO);

        // 4. 存入 Redis 缓存
        ServiceHelper.putToCache(redisTemplate, objectMapper, cacheKey, detail);

        return Result.success(detail);
    }

    /**
     * 更新相册信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<AlbumVO> updateAlbum(Integer userId, Long albumId, AlbumUpdateDTO dto) {
        // 1. 校验相册存在
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            return Result.error(ResultCode.NOT_FOUND, "相册不存在");
        }

        // 2. 更新
        String name = StringUtils.hasText(dto.getName()) ? dto.getName() : album.getName();
        String desc = dto.getDescription();
        albumMapper.updateNameAndDescription(albumId, name, desc);

        // 3. 清除详情缓存
        clearAlbumDetailCache(albumId, userId);

        // 4. 返回更新后的相册信息
        AlbumVO vo = albumMapper.selectAlbumById(albumId);
        return Result.success(vo);
    }

    /**
     * 删除相册（同时删除关联照片的 OSS 文件 + 物理删除独占照片）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteAlbum(Integer userId, Long albumId) {
        // 1. 校验相册存在
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            return Result.error(ResultCode.NOT_FOUND, "相册不存在");
        }
        
        // 保存相册名称（删除后无法获取）
        String albumName = album.getName();

        // 2. 查询相册中仅属于该相册的独占照片（不被其他相册引用）
        List<Photo> orphanedPhotos = albumMapper.selectOrphanedPhotosByAlbumId(albumId);

        // 3. 从阿里云 OSS 删除独占照片文件
        List<Long> orphanedPhotoIds = new ArrayList<>();
        for (Photo photo : orphanedPhotos) {
            if (photo.getStoragePath() != null) {
                try {
                    String objectKey = ServiceHelper.extractObjectKey(photo.getStoragePath());
                    if (objectKey != null) {
                        aliyunOSSUtils.deleteFile(objectKey);
                        log.info("OSS照片删除成功, photoId: {}, objectKey: {}", photo.getId(), objectKey);
                    }
                } catch (Exception e) {
                    log.warn("OSS照片删除失败, photoId: {}, storagePath: {}", photo.getId(), photo.getStoragePath(), e);
                }
            }
            orphanedPhotoIds.add(photo.getId());
        }

        // 4. 物理删除独占照片记录
        if (!orphanedPhotoIds.isEmpty()) {
            albumMapper.deletePhotosByIds(orphanedPhotoIds);
        }

        // 5. 删除相册下的所有照片关联（先记录所有照片ID，用于清除缓存）
        List<Long> allPhotoIds = albumMapper.selectPhotoIdsByAlbumId(albumId);
        albumMapper.deletePhotoAlbumsByAlbumId(albumId);

        // 6. 删除相册
        albumMapper.deleteById(albumId);

        // 7. 清除缓存
        // 7.1 清除相册详情缓存 + 列表缓存
        clearAlbumDetailCache(albumId, userId);

        // 7.2 清除相册内所有照片的详情缓存（Redis 故障不应阻断删除业务）
        if (allPhotoIds != null) {
            for (Long pid : allPhotoIds) {
                try {
                    redisTemplate.delete(PhotoConstant.PHOTO_DETAIL_PREFIX + pid);
                } catch (Exception e) {
                    log.warn("清除照片详情缓存失败, photoId: {}", pid, e);
                }
            }
        }

        User user = userMapper.selectById(userId);
        if (user != null) {
            try {
                clearUserStatsCache(user);
            } catch (Exception e) {
                log.warn("清除用户 stats 缓存失败, userId: {}", userId, e);
            }
        }

        log.info("用户 {} 删除相册成功, albumId: {}, 删除OSS照片: {} 张, 物理删除照片: {} 张",
                userId, albumId, orphanedPhotos.size(), orphanedPhotoIds.size());
        
        // 事务提交后通知伴侣
        final String nickname = user.getNickname() != null ? user.getNickname() : "对方";
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyPartner(userId, nickname + " 删除了相册《" + albumName + "》");
            }
        });
        
        return Result.success(null);
    }

    /**
     * 批量添加照片到相册
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> addPhotosToAlbum(Integer userId, Long albumId, List<Long> photoIds) {
        // 1. 校验相册存在
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            return Result.error(ResultCode.NOT_FOUND, "相册不存在");
        }

        // 2. 批量插入
        if (photoIds != null && !photoIds.isEmpty()) {
            photoAlbumMapper.batchInsert(albumId, photoIds);
        }

        // 3. 清除相册详情缓存
        clearAlbumDetailCache(albumId);

        log.info("用户 {} 添加 {} 张照片到相册 {}", userId, photoIds != null ? photoIds.size() : 0, albumId);
        return Result.success(null);
    }

    /**
     * 从相册移除照片
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> removePhotoFromAlbum(Integer userId, Long albumId, Long photoId) {
        // 1. 校验相册存在
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            return Result.error(ResultCode.NOT_FOUND, "相册不存在");
        }

        // 2. 移除
        photoAlbumMapper.deleteByAlbumIdAndPhotoId(albumId, photoId);

        // 3. 清除相册详情缓存
        clearAlbumDetailCache(albumId, userId);

        log.info("用户 {} 从相册 {} 移除照片 {}", userId, albumId, photoId);
        return Result.success(null);
    }

    /**
     * 清除相册详情缓存 + 相册列表缓存
     */
    @Override
    public void clearAlbumDetailCache(Long albumId, Integer userId) {
        // 清除详情缓存（使用 scan 替代 keys，避免 Redis 阻塞）
        String pattern = AlbumConstant.ALBUM_DETAIL_PREFIX + albumId + ":*";
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("扫描相册详情缓存失败, albumId: {}, pattern: {}", albumId, pattern, e);
        }
        if (!keys.isEmpty()) {
            try {
                redisTemplate.delete(keys);
                log.debug("已清除相册 {} 的详情缓存，共 {} 条", albumId, keys.size());
            } catch (Exception e) {
                log.warn("清除相册详情缓存失败, albumId: {}, keyCount: {}", albumId, keys.size(), e);
            }
        }

        // 清除相册列表缓存
        User user = userMapper.selectById(userId);
        if (user != null) {
            clearAlbumListCache(user);
        }
    }

    @Override
    public void clearAlbumDetailCache(Long albumId) {
        clearAlbumDetailCache(albumId, null);
    }

    private void clearAlbumListCache(User user) {
        // 有 groupId 用 groupId，否则用 userId
        String cacheKey = ServiceHelper.buildCacheKey(
                AlbumConstant.ALBUM_LIST_PREFIX,
                user.getGroupId(),
                user.getId()
        );
        redisTemplate.delete(cacheKey);
        log.debug("已清除相册列表缓存, key: {}", cacheKey);
    }

    private void clearUserStatsCache(User user) {
        // 有 groupId 用 groupId，否则用 userId
        userService.clearUserStatsCache(user.getId().intValue());
    }

    /**
     * 向伴侣发送通知（存入数据库 + SSE推送 + 邮件通知）
     *
     * @param userId 当前用户ID
     * @param text 通知内容
     */
    private void notifyPartner(Integer userId, String text) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null || user.getPartnerId() == null) {
                log.debug("用户未绑定伴侣，跳过通知, userId: {}", userId);
                return;
            }
            Integer partnerId = user.getPartnerId().intValue();
            
            // 1. SSE + DB 通知
            notificationService.createAndPushNotification(partnerId, text);
            
            // 2. 邮件通知（根据伴侣的通知设置判断是否发送）
            emailNotificationService.sendEmailToPartner(userId, text);
            
            log.info("已向伴侣发送通知, userId: {}, partnerId: {}, text: {}", userId, partnerId, text);
        } catch (Exception e) {
            log.error("发送伴侣通知失败, userId: {}, text: {}", userId, text, e);
        }
    }
}
