package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.ai.service.AiRecommendService;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.DatePlanConstant;
import com.example.lovemap.mapper.DatePlanMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.dto.AiRecommendRequestDTO;
import com.example.lovemap.model.dto.DatePlanCreateDTO;
import com.example.lovemap.model.dto.DatePlanStatusDTO;
import com.example.lovemap.model.dto.DatePlanUpdateDTO;
import com.example.lovemap.model.entity.DatePlan;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.AiRecommendVO;
import com.example.lovemap.model.vo.DatePlanVO;
import com.example.lovemap.service.DatePlanService;
import com.example.lovemap.utils.FileTypeUtils;
import com.example.lovemap.utils.storage.FileStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 约会计划服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatePlanServiceImpl implements DatePlanService {

    private final DatePlanMapper datePlanMapper;
    private final UserMapper userMapper;
    private final AiRecommendService aiRecommendService;
    private final ObjectMapper objectMapper;
    private final FileStorage fileStorage;

    @Override
    public Result<List<DatePlanVO>> listDatePlans(Integer userId) {
        return withUserValidation(userId, (user, groupId) -> {
            List<DatePlan> list = datePlanMapper.selectByGroupOrUser(groupId, user.getId());
            return Result.success(list.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList()));
        });
    }

    @Override
    public Result<DatePlanVO> getDatePlanDetail(Integer userId, Long id) {
        return withRecordValidation(userId, id, (user, record) -> Result.success(convertToVO(record)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DatePlanVO> createDatePlan(Integer userId, DatePlanCreateDTO dto) {
        return withUserValidation(userId, (user, groupId) -> {
            DatePlan plan = new DatePlan();
            plan.setGroupId(groupId);
            plan.setTitle(dto.getTitle());
            plan.setPlanDate(dto.getPlanDate());
            plan.setTimeSlot(dto.getTimeSlot());
            plan.setScenes(toJson(dto.getScenes()));
            plan.setLocation(dto.getLocation());
            plan.setLocationSuggestion(dto.getLocationSuggestion());
            plan.setBudget(dto.getBudget());
            plan.setReason(dto.getReason());
            plan.setTips(toJson(dto.getTips()));
            plan.setDescription(dto.getDescription());
            plan.setStatus(DatePlanConstant.STATUS_PLANNED);
            plan.setCreatedBy(user.getId());

            datePlanMapper.insert(plan);
            log.info("用户 {} 创建约会计划成功, planId: {}", userId, plan.getId());
            return Result.success(convertToVO(plan));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DatePlanVO> updateDatePlan(Integer userId, Long id, DatePlanUpdateDTO dto) {
        return withRecordValidation(userId, id, (user, existing) -> {
            if (dto.getTitle() != null) {
                existing.setTitle(dto.getTitle());
            }
            if (dto.getPlanDate() != null) {
                existing.setPlanDate(dto.getPlanDate());
            }
            if (dto.getTimeSlot() != null) {
                existing.setTimeSlot(dto.getTimeSlot());
            }
            if (dto.getScenes() != null) {
                existing.setScenes(toJson(dto.getScenes()));
            }
            if (dto.getLocation() != null) {
                existing.setLocation(dto.getLocation());
            }
            if (dto.getLocationSuggestion() != null) {
                existing.setLocationSuggestion(dto.getLocationSuggestion());
            }
            if (dto.getBudget() != null) {
                existing.setBudget(dto.getBudget());
            }
            if (dto.getReason() != null) {
                existing.setReason(dto.getReason());
            }
            if (dto.getTips() != null) {
                existing.setTips(toJson(dto.getTips()));
            }
            if (dto.getDescription() != null) {
                existing.setDescription(dto.getDescription());
            }

            datePlanMapper.update(existing, user.getId());
            log.info("用户 {} 更新约会计划成功, planId: {}", userId, id);
            return Result.success(convertToVO(existing));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DatePlanVO> updateDatePlanStatus(Integer userId, Long id, DatePlanStatusDTO dto) {
        return withRecordValidation(userId, id, (user, existing) -> {
            Integer status = dto.getStatus();
            if (status != DatePlanConstant.STATUS_PLANNED
                    && status != DatePlanConstant.STATUS_COMPLETED
                    && status != DatePlanConstant.STATUS_CANCELLED) {
                return Result.badRequest("状态值非法");
            }

            datePlanMapper.updateStatus(id, existing.getGroupId(), user.getId(), status);
            existing.setStatus(status);
            log.info("用户 {} 更新约会计划状态成功, planId: {}, status: {}", userId, id, status);
            return Result.success(convertToVO(existing));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DatePlanVO> uploadDatePlanPhoto(Integer userId, Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.badRequest("请选择要上传的照片");
        }
        if (file.getSize() > DatePlanConstant.MAX_PHOTO_SIZE_BYTES) {
            return Result.badRequest("照片大小不能超过 10MB");
        }
        String extension = FileTypeUtils.getFileExtension(file.getOriginalFilename());
        if (!FileTypeUtils.isValidImageType(extension)) {
            return Result.badRequest("仅支持上传图片文件");
        }

        return withRecordValidation(userId, id, (user, existing) -> {
            String objectKey = DatePlanConstant.OSS_PHOTO_PREFIX + existing.getId() + "_"
                    + System.currentTimeMillis() + "." + extension.toLowerCase();
            String url;
            try {
                url = fileStorage.uploadFile(file, objectKey);
            } catch (Exception e) {
                log.error("用户 {} 上传约会照片失败, planId: {}", userId, id, e);
                return Result.error(ResultCode.INTERNAL_SERVER_ERROR, "照片上传失败，请稍后重试");
            }

            List<String> photos = new ArrayList<>(fromJson(existing.getPhotos()));
            photos.add(url);
            existing.setPhotos(toJson(photos));
            datePlanMapper.update(existing, user.getId());
            log.info("用户 {} 上传约会照片成功, planId: {}, url: {}", userId, id, url);
            return Result.success(convertToVO(existing));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteDatePlan(Integer userId, Long id) {
        return withRecordValidation(userId, id, (user, existing) -> {
            datePlanMapper.softDelete(id, existing.getGroupId(), user.getId());
            log.info("用户 {} 删除约会计划成功, planId: {}", userId, id);
            return Result.success(null);
        });
    }

    @Override
    public Result<AiRecommendVO> recommendDatePlans(Integer userId, AiRecommendRequestDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        String content = aiRecommendService.recommendDatePlan(dto.getPrompt());
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

    private <T> Result<T> withRecordValidation(Integer userId, Long id, BiFunction<User, DatePlan, Result<T>> action) {
        return withUserValidation(userId, (user, groupId) -> {
            DatePlan record = datePlanMapper.selectByIdAndGroupOrUser(id, groupId, user.getId());
            if (record == null) {
                return Result.error(ResultCode.NOT_FOUND, "约会计划不存在");
            }
            return action.apply(user, record);
        });
    }

    // ==================== 转换 ====================

    private DatePlanVO convertToVO(DatePlan plan) {
        DatePlanVO vo = new DatePlanVO();
        vo.setId(plan.getId());
        vo.setGroupId(plan.getGroupId());
        vo.setTitle(plan.getTitle());
        vo.setPlanDate(plan.getPlanDate());
        vo.setTimeSlot(plan.getTimeSlot());
        vo.setScenes(fromJson(plan.getScenes()));
        vo.setLocation(plan.getLocation());
        vo.setLocationSuggestion(plan.getLocationSuggestion());
        vo.setBudget(plan.getBudget());
        vo.setReason(plan.getReason());
        vo.setTips(fromJson(plan.getTips()));
        vo.setPhotos(fromJson(plan.getPhotos()));
        vo.setDescription(plan.getDescription());
        vo.setStatus(plan.getStatus());
        vo.setCreatedBy(plan.getCreatedBy());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        return vo;
    }

    private String toJson(List<String> list) {
        if (list == null) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", list, e);
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("JSON 反序列化失败: {}", json, e);
            return Collections.emptyList();
        }
    }
}
