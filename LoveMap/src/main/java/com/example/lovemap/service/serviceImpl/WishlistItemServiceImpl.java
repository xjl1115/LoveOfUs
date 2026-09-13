package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.ai.service.AiRecommendService;
import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.ServiceHelper;
import com.example.lovemap.common.constant.WishlistConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.mapper.WishlistItemMapper;
import com.example.lovemap.model.dto.AiRecommendRequestDTO;
import com.example.lovemap.model.dto.WishlistItemCreateDTO;
import com.example.lovemap.model.dto.WishlistItemStatusDTO;
import com.example.lovemap.model.dto.WishlistItemUpdateDTO;
import com.example.lovemap.model.dto.WishlistProgressDTO;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.entity.WishlistItem;
import com.example.lovemap.model.vo.AiRecommendVO;
import com.example.lovemap.model.vo.WishlistItemVO;
import com.example.lovemap.service.WishlistItemService;
import com.example.lovemap.utils.FileTypeUtils;
import com.example.lovemap.utils.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 心愿清单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistItemServiceImpl implements WishlistItemService {

    private final WishlistItemMapper wishlistItemMapper;
    private final UserMapper userMapper;
    private final AiRecommendService aiRecommendService;
    private final FileStorage fileStorage;

    /** 代理读取存储中的照片：服务端转发，避免前端跨域取图污染 canvas 导致卡片导出失败 */
    private static final HttpClient PHOTO_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public Result<List<WishlistItemVO>> listWishlistItems(Integer userId) {
        return withUserValidation(userId, (user, groupId) -> {
            List<WishlistItem> list = wishlistItemMapper.selectByGroupOrUser(groupId, user.getId());
            return Result.success(list.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList()));
        });
    }

    @Override
    public Result<WishlistItemVO> getWishlistItemDetail(Integer userId, Long id) {
        return withRecordValidation(userId, id, (user, record) -> Result.success(convertToVO(record)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<WishlistItemVO> createWishlistItem(Integer userId, WishlistItemCreateDTO dto) {
        return withUserValidation(userId, (user, groupId) -> {
            WishlistItem item = new WishlistItem();
            item.setGroupId(groupId);
            item.setTitle(dto.getTitle());
            item.setDescription(dto.getDescription());
            item.setCategory(dto.getCategory());
            item.setIcon(dto.getIcon());
            item.setPriority(dto.getPriority() != null ? dto.getPriority() : WishlistConstant.PRIORITY_NORMAL);
            item.setTargetValue(dto.getTargetValue() != null ? dto.getTargetValue() : 1);
            item.setCurrentValue(0);
            item.setUnit(dto.getUnit());
            item.setTargetDate(dto.getTargetDate());
            item.setNeedBothConfirm(dto.getNeedBothConfirm() != null ? dto.getNeedBothConfirm() : 0);
            item.setStatus(WishlistConstant.STATUS_PENDING);
            item.setCreatedBy(user.getId());

            wishlistItemMapper.insert(item);
            log.info("用户 {} 创建心愿项成功, itemId: {}", userId, item.getId());
            return Result.success(convertToVO(item));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<WishlistItemVO> updateWishlistItem(Integer userId, Long id, WishlistItemUpdateDTO dto) {
        return withRecordValidation(userId, id, (user, existing) -> {
            if (dto.getTitle() != null) {
                existing.setTitle(dto.getTitle());
            }
            if (dto.getDescription() != null) {
                existing.setDescription(dto.getDescription());
            }
            if (dto.getPriority() != null) {
                existing.setPriority(dto.getPriority());
            }
            if (dto.getCategory() != null) {
                existing.setCategory(dto.getCategory());
            }
            if (dto.getIcon() != null) {
                existing.setIcon(dto.getIcon());
            }
            if (dto.getTargetValue() != null) {
                existing.setTargetValue(dto.getTargetValue());
            }
            if (dto.getUnit() != null) {
                existing.setUnit(dto.getUnit());
            }
            if (dto.getTargetDate() != null) {
                existing.setTargetDate(dto.getTargetDate());
            }
            if (dto.getNeedBothConfirm() != null) {
                existing.setNeedBothConfirm(dto.getNeedBothConfirm());
            }

            wishlistItemMapper.update(existing, user.getId());
            log.info("用户 {} 更新心愿项成功, itemId: {}", userId, id);
            return Result.success(convertToVO(existing));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<WishlistItemVO> updateWishlistItemStatus(Integer userId, Long id, WishlistItemStatusDTO dto) {
        return withRecordValidation(userId, id, (user, existing) -> {
            Integer status = dto.getStatus();
            if (status != WishlistConstant.STATUS_PENDING && status != WishlistConstant.STATUS_COMPLETED) {
                return Result.badRequest("状态值非法");
            }

            existing.setStatus(status);
            if (status == WishlistConstant.STATUS_COMPLETED) {
                existing.setCurrentValue(existing.getTargetValue());
                existing.setAchievedAt(LocalDateTime.now());
                existing.setAchievedNote(dto.getNote());
            } else {
                existing.setCurrentValue(0);
                existing.setAchievedAt(null);
                existing.setAchievedNote(null);
            }

            wishlistItemMapper.updateStatus(id, existing.getGroupId(), user.getId(), status);
            wishlistItemMapper.update(existing, user.getId());
            log.info("用户 {} 更新心愿项状态成功, itemId: {}, status: {}", userId, id, status);
            return Result.success(convertToVO(existing));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<WishlistItemVO> adjustWishlistItemProgress(Integer userId, Long id, WishlistProgressDTO dto) {
        return withRecordValidation(userId, id, (user, existing) -> {
            if (dto.getDelta() == null || dto.getDelta() == 0) {
                return Result.success(convertToVO(existing));
            }
            wishlistItemMapper.adjustProgress(id, existing.getGroupId(), user.getId(), dto.getDelta());
            WishlistItem updated = wishlistItemMapper.selectByIdAndGroupOrUser(id, existing.getGroupId(), user.getId());
            log.info("用户 {} 调整心愿进度成功, itemId: {}, delta: {}", userId, id, dto.getDelta());
            return Result.success(convertToVO(updated != null ? updated : existing));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<WishlistItemVO> uploadWishlistItemPhoto(Integer userId, Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.badRequest("请选择要上传的照片");
        }
        if (file.getSize() > WishlistConstant.MAX_PHOTO_SIZE_BYTES) {
            return Result.badRequest("照片大小不能超过 10MB");
        }
        String extension = FileTypeUtils.getFileExtension(file.getOriginalFilename());
        if (!FileTypeUtils.isValidImageType(extension)) {
            return Result.badRequest("仅支持上传图片文件");
        }

        return withRecordValidation(userId, id, (user, existing) -> {
            String objectKey = WishlistConstant.OSS_PHOTO_PREFIX + existing.getId() + "_"
                    + System.currentTimeMillis() + "." + extension.toLowerCase();
            String url;
            try {
                url = fileStorage.uploadFile(file, objectKey);
            } catch (Exception e) {
                log.error("用户 {} 上传心愿纪念照片失败, itemId: {}", userId, id, e);
                return Result.error(ResultCode.INTERNAL_SERVER_ERROR, "照片上传失败，请稍后重试");
            }

            String previousUrl = existing.getAchievedPhotoUrl();
            existing.setAchievedPhotoUrl(url);
            wishlistItemMapper.update(existing, user.getId());
            // 替换照片时清理旧文件，避免存储中留下孤儿文件
            if (previousUrl != null && !previousUrl.isBlank() && !previousUrl.equals(url)) {
                String previousKey = ServiceHelper.extractObjectKey(previousUrl);
                if (previousKey != null) {
                    try {
                        fileStorage.delete(previousKey);
                    } catch (Exception e) {
                        log.warn("清理旧纪念照片失败, itemId: {}, url: {}", id, previousUrl, e);
                    }
                }
            }
            log.info("用户 {} 上传心愿纪念照片成功, itemId: {}", userId, id);
            return Result.success(convertToVO(existing));
        });
    }

    @Override
    public ResponseEntity<Resource> loadWishlistItemPhoto(Integer userId, Long id) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        WishlistItem item = wishlistItemMapper.selectByIdAndGroupOrUser(id, user.getGroupId(), user.getId());
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "心愿项不存在");
        }
        String photoUrl = item.getAchievedPhotoUrl();
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该心愿还没有纪念照片");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(photoUrl))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = PHOTO_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() != 200 || body == null || body.length == 0) {
                throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "照片读取失败，请稍后重试");
            }
            MediaType mediaType = response.headers()
                    .firstValue("Content-Type")
                    .map(WishlistItemServiceImpl::parseMediaType)
                    .orElse(MediaType.IMAGE_JPEG);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                    .body(new ByteArrayResource(body));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取心愿纪念照片失败, itemId: {}, url: {}", id, photoUrl, e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "照片读取失败，请稍后重试");
        }
    }

    private static MediaType parseMediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception e) {
            return MediaType.IMAGE_JPEG;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteWishlistItem(Integer userId, Long id) {
        return withRecordValidation(userId, id, (user, existing) -> {
            wishlistItemMapper.softDelete(id, existing.getGroupId(), user.getId());
            log.info("用户 {} 删除心愿项成功, itemId: {}", userId, id);
            return Result.success(null);
        });
    }

    @Override
    public Result<AiRecommendVO> recommendWishlistItems(Integer userId, AiRecommendRequestDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 已有心愿标题交给 AI 做去重，避免重复推荐清单中已存在的事件
        List<String> existingTitles = wishlistItemMapper.selectByGroupOrUser(user.getGroupId(), user.getId())
                .stream()
                .map(WishlistItem::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .limit(50)
                .collect(Collectors.toList());
        String content = aiRecommendService.recommendWishlist(dto.getPrompt(), existingTitles);
        AiRecommendVO vo = new AiRecommendVO();
        vo.setContent(content);
        return Result.success(vo);
    }

    // ==================== 校验模板 ====================

    private <T> Result<T> withUserValidation(Integer userId, BiFunction<User, Long, Result<T>> action) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        Long groupId = user.getGroupId();
        return action.apply(user, groupId);
    }

    private <T> Result<T> withRecordValidation(Integer userId, Long id, BiFunction<User, WishlistItem, Result<T>> action) {
        return withUserValidation(userId, (user, groupId) -> {
            WishlistItem record = wishlistItemMapper.selectByIdAndGroupOrUser(id, groupId, user.getId());
            if (record == null) {
                return Result.error(ResultCode.NOT_FOUND, "心愿项不存在");
            }
            return action.apply(user, record);
        });
    }

    // ==================== 转换 ====================

    private WishlistItemVO convertToVO(WishlistItem item) {
        WishlistItemVO vo = new WishlistItemVO();
        vo.setId(item.getId());
        vo.setGroupId(item.getGroupId());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setCategory(item.getCategory());
        vo.setIcon(item.getIcon());
        vo.setPriority(item.getPriority());
        vo.setTargetValue(item.getTargetValue());
        vo.setCurrentValue(item.getCurrentValue());
        vo.setUnit(item.getUnit());
        vo.setStatus(item.getStatus());
        vo.setTargetDate(item.getTargetDate());
        vo.setNeedBothConfirm(item.getNeedBothConfirm());
        vo.setAchievedAt(item.getAchievedAt());
        vo.setAchievedNote(item.getAchievedNote());
        vo.setAchievedPhotoUrl(item.getAchievedPhotoUrl());
        vo.setCreatedBy(item.getCreatedBy());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }
}
