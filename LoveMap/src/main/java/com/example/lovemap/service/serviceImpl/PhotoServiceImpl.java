package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.ServiceHelper;
import com.example.lovemap.common.constant.AlbumConstant;
import com.example.lovemap.common.constant.PhotoConstant;
import com.example.lovemap.common.constant.UserConstant;
import com.example.lovemap.mapper.AlbumMapper;
import com.example.lovemap.mapper.PhotoAlbumMapper;
import com.example.lovemap.mapper.PhotoMapper;
import com.example.lovemap.mapper.ProvenceMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.dto.PhotoUploadDTO;
import com.example.lovemap.model.entity.Album;
import com.example.lovemap.model.entity.Photo;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.PhotoDetailVO;
import com.example.lovemap.model.vo.PhotoUploadVO;
import com.example.lovemap.model.vo.TimelineGroupVO;
import com.example.lovemap.model.vo.TimelinePhotoVO;
import com.example.lovemap.model.vo.TimelineResultVO;
import com.example.lovemap.service.EmailNotificationService;
import com.example.lovemap.service.NotificationService;
import com.example.lovemap.service.PhotoService;
import com.example.lovemap.service.UserService;
import com.example.lovemap.utils.AliyunOSSUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 照片服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoServiceImpl implements PhotoService {

    private final PhotoMapper photoMapper;
    private final PhotoAlbumMapper photoAlbumMapper;
    private final AlbumMapper albumMapper;
    private final UserMapper userMapper;
    private final ProvenceMapper provenceMapper;
    private final UserService userService;
    private final AliyunOSSUtils aliyunOSSUtils;
    private final StringRedisTemplate redisTemplate;
    private final ThreadPoolTaskExecutor photoUploadExecutor;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;

    // Lua 脚本（延迟加载）
    private DefaultRedisScript<Long> lockAcquireScript;
    private DefaultRedisScript<Long> lockReleaseScript;

    private DefaultRedisScript<Long> getLockAcquireScript() {
        if (lockAcquireScript == null) {
            lockAcquireScript = new DefaultRedisScript<>();
            lockAcquireScript.setLocation(new ClassPathResource("lua/lock_acquire.lua"));
            lockAcquireScript.setResultType(Long.class);
        }
        return lockAcquireScript;
    }

    private DefaultRedisScript<Long> getLockReleaseScript() {
        if (lockReleaseScript == null) {
            lockReleaseScript = new DefaultRedisScript<>();
            lockReleaseScript.setLocation(new ClassPathResource("lua/lock_release.lua"));
            lockReleaseScript.setResultType(Long.class);
        }
        return lockReleaseScript;
    }

    /**
     * 上传照片
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<PhotoUploadVO> uploadPhotos(Integer userId, List<MultipartFile> files, PhotoUploadDTO dto) {
        // 1. 参数校验
        if (CollectionUtils.isEmpty(files)) {
            return Result.badRequest("请选择要上传的照片文件");
        }
        if (files.size() > 20) {
            return Result.badRequest("单次最多上传 20 张照片");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }

        // 2. 校验相册（如果指定了 albumId）
        Long albumId = null;
        Album album = null;
        if (dto.getAlbumId() != null && dto.getAlbumId() > 0) {
            album = albumMapper.selectById(dto.getAlbumId());
            if (album == null) {
                return Result.notFound("相册不存在");
            }
            albumId = dto.getAlbumId();
        }

        // 3. 分布式锁（以 albumId + userId 为粒度，防止并发上传冲突）
        String lockKey = PhotoConstant.UPLOAD_LOCK_KEY_PREFIX + (albumId != null ? albumId : "0") + ":" + userId;
        Long locked = redisTemplate.execute(getLockAcquireScript(), Collections.singletonList(lockKey), String.valueOf(PhotoConstant.UPLOAD_LOCK_TIMEOUT));
        if (locked == null || locked == 0) {
            log.warn("获取上传锁失败, userId: {}, albumId: {}, 可能有其他上传任务正在进行", userId, albumId);
            return Result.error(ResultCode.SERVICE_UNAVAILABLE, "系统繁忙，请稍后再试");
        }

        try {
            // 4. 异步上传所有文件到 OSS，收集结果
            List<CompletableFuture<UploadResult>> futures = new ArrayList<>(files.size());
            for (MultipartFile file : files) {
                CompletableFuture<UploadResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        String url = aliyunOSSUtils.asyncUploadPhoto(file, userId);
                        UploadResult ur = new UploadResult();
                        ur.success = true;
                        ur.url = url;
                        ur.originalName = file.getOriginalFilename();
                        ur.fileSize = file.getSize();
                        ur.mimeType = file.getContentType();
                        return ur;
                    } catch (IOException e) {
                        log.error("照片上传OSS失败, fileName: {}", file.getOriginalFilename(), e);
                        UploadResult ur = new UploadResult();
                        ur.success = false;
                        ur.errorMessage = e.getMessage();
                        return ur;
                    }
                }, photoUploadExecutor);
                futures.add(future);
            }

            // 等待所有上传完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 5. 收集结果
            List<Photo> photos = new ArrayList<>();
            List<String> failedFiles = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                UploadResult ur = futures.get(i).join();
                if (ur.success) {
                    Photo photo = new Photo();
                    photo.setUserId(userId.longValue());
                    photo.setGroupId(user.getGroupId());
                    photo.setStoragePath(ur.url);
                    photo.setTakenDate(dto.getTakenDate() != null ? LocalDate.parse(dto.getTakenDate()) : null);
                    photo.setLocationName(dto.getLocationName());
                    photo.setCity(dto.getCity());
                    photo.setProvince(convertProvinceToId(dto.getProvince()));
                    photo.setCountry(dto.getCountry());
                    photo.setDescription(dto.getDescription());
                    photos.add(photo);
                } else {
                    failedFiles.add(files.get(i).getOriginalFilename() + ": " + ur.errorMessage);
                }
            }

            if (photos.isEmpty()) {
                return Result.badRequest("所有照片上传失败");
            }

            // 6. 批量插入 photo 表
            photoMapper.batchInsert(photos);

            // 7. 插入 photo_album 关联
            List<Long> photoIds = new ArrayList<>(photos.size());
            for (Photo photo : photos) {
                photoIds.add(photo.getId());
            }
            if (albumId != null && !photoIds.isEmpty()) {
                photoAlbumMapper.batchInsert(albumId, photoIds);
            }

            // 8. 构建返回结果
            PhotoUploadVO vo = new PhotoUploadVO();
            vo.setPhotoIds(photoIds);
            vo.setSuccessCount(photoIds.size());
            vo.setAlbumId(albumId);

            // 9. 在事务主体内清理缓存（Redis 故障不应阻断业务：用 try-catch + warn，下次请求自动重建）
            final Long finalAlbumId = albumId;
            final int count = photoIds.size();
            final String nickname = user.getNickname() != null ? user.getNickname() : "对方";
            final String albumName = album != null ? album.getName() : "相册";
            final String albumListKey = AlbumConstant.ALBUM_LIST_PREFIX +
                    (user.getGroupId() != null ? user.getGroupId() : userId);
            log.info("上传照片成功，开始清理相册缓存, userId: {}, albumListKey: {}, finalAlbumId: {}",
                    userId, albumListKey, finalAlbumId);

            try {
                userService.clearUserStatsCache(userId);
                log.debug("已清除用户 stats 缓存, userId: {}", userId);
            } catch (Exception e) {
                log.warn("清除用户 stats 缓存失败, userId: {}", userId, e);
            }

            try {
                Boolean deleted = redisTemplate.delete(albumListKey);
                log.info("相册列表缓存清理完成, key: {}, deleted: {}", albumListKey, deleted);
            } catch (Exception e) {
                log.warn("清除相册列表缓存失败, key: {}", albumListKey, e);
            }

            // 清除该用户/群组下所有相册的详情缓存（不仅限于上传目标相册，避免封面图关联的相册缓存不一致）
            try {
                List<Long> albumIds = albumMapper.selectAlbumIdsByGroupOrUser(
                        user.getGroupId(), userId.longValue());
                if (albumIds != null && !albumIds.isEmpty()) {
                    Set<String> detailKeys = new HashSet<>();
                    ScanOptions options = ScanOptions.scanOptions().match(AlbumConstant.ALBUM_DETAIL_PREFIX + "*").count(200).build();
                    try (Cursor<String> cursor = redisTemplate.scan(options)) {
                        while (cursor.hasNext()) {
                            String key = cursor.next();
                            // 只清理属于该用户/群组的相册详情缓存
                            for (Long aid : albumIds) {
                                if (key.startsWith(AlbumConstant.ALBUM_DETAIL_PREFIX + aid + ":")) {
                                    detailKeys.add(key);
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("扫描相册详情缓存失败", e);
                    }
                    if (!detailKeys.isEmpty()) {
                        try {
                            redisTemplate.delete(detailKeys);
                            log.info("已清除相册详情缓存，共 {} 条, 涉及 {} 个相册", detailKeys.size(), albumIds.size());
                        } catch (Exception e) {
                            log.warn("清除相册详情缓存失败, count: {}", detailKeys.size(), e);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("清除相册详情缓存流程异常, userId: {}", userId, e);
            }

            // 10. 事务提交后通知伴侣（通知是次要路径，必须在 commit 后）
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyPartner(userId, nickname + " 向 《" + albumName + "》 上传了 " + count + " 张新照片");
                }
            });

            if (failedFiles.isEmpty()) {
                log.info("用户 {} 上传 {} 张照片成功, albumId: {}", userId, photoIds.size(), albumId);
                return Result.success(vo);
            } else {
                log.warn("用户 {} 上传照片部分失败, 成功: {}, 失败: {}, 详情: {}",
                        userId, photoIds.size(), failedFiles.size(), failedFiles);
                return Result.success("部分照片上传失败：" + String.join("; ", failedFiles), vo);
            }

        } finally {
            // 10. 释放分布式锁
            redisTemplate.execute(getLockReleaseScript(), Collections.singletonList(lockKey));
        }
    }

    /**
     * 上传结果内部类
     */
    private static class UploadResult {
        boolean success;
        String url;
        String originalName;
        Long fileSize;
        String mimeType;
        String errorMessage;
    }

    /**
     * 将省份名称转换为省份ID
     *
     * @param provinceName 省份名称（如"湖北"）
     * @return 省份ID，不存在或名称为空返回 null
     */
    private Integer convertProvinceToId(String provinceName) {
        if (provinceName == null || provinceName.trim().isEmpty()) {
            return null;
        }
        Integer id = provenceMapper.selectIdByName(provinceName.trim());
        if (id == null) {
            log.warn("未找到省份名称对应的ID: {}", provinceName);
        }
        return id;
    }

    /**
     * 获取时光轴（图片列表）
     */
    @Override
    public Result<TimelineResultVO> getTimeline(Integer userId, Integer page, Integer size, String provence, String city, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        log.info("获取用户 {} 的时光轴, page: {}, size: {}, provence: {}, city: {}", userId, page, size, provence, city);
        // 1. 参数校验
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1 || size > 100) {
            size = 20;
        }

        // 2. 查询用户，确定查询范围
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }

        // 空字符串转为 null
        if (provence != null && provence.trim().isEmpty()) {
            provence = null;
        }
        if (city != null && city.trim().isEmpty()) {
            city = null;
        }

        Long groupId = user.getGroupId();
        Long total;
        List<TimelinePhotoVO> photos;

        int offset = (page - 1) * size;

        if (groupId != null) {
            total = photoMapper.countTimelineByGroupId(groupId, provence, city, startDate, endDate);
            photos = photoMapper.selectTimelineByGroupId(groupId, provence, city, startDate, endDate, offset, size);
        } else {
            total = photoMapper.countTimelineByUserId(userId.longValue(), provence, city, startDate, endDate);
            photos = photoMapper.selectTimelineByUserId(userId.longValue(), provence, city, startDate, endDate, offset, size);
        }

        // 3. 按月份分组（使用 LinkedHashMap 保持有序）
        Map<String, List<TimelinePhotoVO>> groupMap = new LinkedHashMap<>();
        for (TimelinePhotoVO photo : photos) {
            String takenDate = photo.getTakenDate();
            String month = (takenDate != null && takenDate.length() >= 7) ? takenDate.substring(0, 7) : null;
            if (month == null) {
                continue;
            }
            groupMap.computeIfAbsent(month, k -> new ArrayList<>()).add(photo);
        }

        // 4. 构建返回结果
        List<TimelineGroupVO> records = new ArrayList<>();
        for (Map.Entry<String, List<TimelinePhotoVO>> entry : groupMap.entrySet()) {
            TimelineGroupVO group = new TimelineGroupVO();
            group.setDate(entry.getKey());
            group.setPhotos(entry.getValue());
            records.add(group);
        }

        TimelineResultVO result = new TimelineResultVO();
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setRecords(records);
        return Result.success(result);
    }

    /**
     * 删除照片
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deletePhoto(Integer userId, Long photoId) {
        // 1. 查询照片
        Photo photo = photoMapper.selectById(photoId);
        if (photo == null) {
            return Result.notFound("照片不存在");
        }

        // 2. 查询用户，校验权限（只能删除自己的照片）
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }
        if (!photo.getUserId().equals(userId.longValue())) {
            return Result.error(ResultCode.FORBIDDEN, "只能删除自己的照片");
        }

        // 3. 查询照片所属的所有相册ID（用于清除缓存）
        List<Long> albumIds = photoAlbumMapper.selectAlbumIdsByPhotoId(photoId);

        // 4. 从阿里云 OSS 删除文件
        if (photo.getStoragePath() != null) {
            try {
                String objectKey = ServiceHelper.extractObjectKey(photo.getStoragePath());
                if (objectKey != null) {
                    aliyunOSSUtils.deleteFile(objectKey);
                    log.info("OSS照片删除成功, photoId: {}, objectKey: {}", photoId, objectKey);
                }
            } catch (Exception e) {
                log.warn("OSS照片删除失败, photoId: {}, storagePath: {}", photoId, photo.getStoragePath(), e);
            }
        }

        // 5. 删除相册关联
        photoAlbumMapper.deleteByPhotoId(photoId);

        // 6. 物理删除照片
        photoMapper.deleteById(photoId);

        // 7. 清除缓存
        // 7.1 清除相册详情缓存（使用 scan 替代 keys，避免 Redis 阻塞）
        if (albumIds != null) {
            for (Long aid : albumIds) {
                String pattern = AlbumConstant.ALBUM_DETAIL_PREFIX + aid + ":*";
                Set<String> keys = new HashSet<>();
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
                try (Cursor<String> cursor = redisTemplate.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(cursor.next());
                    }
                } catch (Exception e) {
                    log.warn("扫描相册详情缓存失败, albumId: {}, pattern: {}", aid, pattern, e);
                }
                if (!keys.isEmpty()) {
                    try {
                        redisTemplate.delete(keys);
                        log.debug("已清除相册 {} 的详情缓存，共 {} 条", aid, keys.size());
                    } catch (Exception e) {
                        log.warn("清除相册详情缓存失败, albumId: {}, count: {}", aid, keys.size(), e);
                    }
                }
            }
        }
        // 7.2 清除相册列表缓存（使用 unlink 异步删除）
        String albumListKey = AlbumConstant.ALBUM_LIST_PREFIX +
                (user.getGroupId() != null ? user.getGroupId() : userId);
        try {
            redisTemplate.delete(albumListKey);
        } catch (Exception e) {
            log.warn("清除相册列表缓存失败, key: {}", albumListKey, e);
        }

        // 7.3 清除用户 stats 缓存（有 groupId 用 groupId，否则用 userId）
        try {
            userService.clearUserStatsCache(userId);
        } catch (Exception e) {
            log.warn("清除用户 stats 缓存失败, userId: {}", userId, e);
        }

        log.info("用户 {} 删除照片成功, photoId: {}", userId, photoId);
        
        // 事务提交后通知伴侣
        final String nickname = user.getNickname() != null ? user.getNickname() : "对方";
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyPartner(userId, nickname + " 删除了一张照片");
            }
        });
        
        return Result.success(null);
    }

    /**
     * 获取照片详情
     */
    @Override
    public Result<PhotoDetailVO> getPhotoDetail(Integer userId, Long photoId) {
        String cacheKey = PhotoConstant.PHOTO_DETAIL_PREFIX + photoId;

        // 1. 尝试从 Redis 缓存获取
        PhotoDetailVO cached = ServiceHelper.getFromCache(redisTemplate, objectMapper, cacheKey, PhotoDetailVO.class);
        if (cached != null) {
            return Result.success(cached);
        }

        // 2. 查询数据库
        PhotoDetailVO detail = photoMapper.selectPhotoDetailById(photoId);
        if (detail == null) {
            return Result.notFound("照片不存在");
        }

        // 3. 查询所属相册
        List<PhotoDetailVO.AlbumInfo> albums = photoAlbumMapper.selectAlbumsByPhotoId(photoId);
        detail.setAlbums(albums);

        // 4. 查询上/下一张照片ID（按上传者自己的照片排序）
        Long prevId = photoMapper.selectPrevPhotoId(photoId, detail.getUploader().getId(), detail.getTakenDate());
        Long nextId = photoMapper.selectNextPhotoId(photoId, detail.getUploader().getId(), detail.getTakenDate());
        detail.setPrevPhotoId(prevId);
        detail.setNextPhotoId(nextId);

        // 5. 存入 Redis 缓存
        ServiceHelper.putToCache(redisTemplate, objectMapper, cacheKey, detail);

        return Result.success(detail);
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