package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.constant.PointsConstant;
import com.example.lovemap.mapper.CoupleTaskMapper;
import com.example.lovemap.mapper.UserTaskMapper;
import com.example.lovemap.model.entity.CoupleTask;
import com.example.lovemap.service.PointsService;
import com.example.lovemap.service.UserTaskTriggerService;
import com.example.lovemap.service.support.CoupleTaskCacheSupport;
import com.example.lovemap.utils.BusinessDateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 情侣任务自动识别实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTaskTriggerServiceImpl implements UserTaskTriggerService {

    private final CoupleTaskMapper taskMapper;
    private final UserTaskMapper userTaskMapper;
    private final PointsService pointsService;
    private final CoupleTaskCacheSupport cacheSupport;
    private final BusinessDateUtils businessDateUtils;

    @Override
    public void onEvent(Integer userId, String completeType) {
        if (userId == null || completeType == null || completeType.isBlank()) {
            return;
        }
        try {
            List<CoupleTask> tasks = taskMapper.selectEnabledByCompleteType(completeType);
            if (tasks == null || tasks.isEmpty()) {
                return;
            }
            boolean changed = false;
            for (CoupleTask task : tasks) {
                String periodKey = businessDateUtils.currentPeriodKey(task.getPeriodType());
                // 未完成时无行，先幂等落行；已存在则由唯一键忽略
                userTaskMapper.insertIgnore(userId.longValue(), task.getId(),
                        task.getPeriodType(), periodKey);
                // 条件更新：仅 status=0 时置为「已完成待领取」，避免重复处理
                int rows = userTaskMapper.markCompleted(userId.longValue(), task.getId(), periodKey);
                if (rows != 1) {
                    continue;
                }
                changed = true;
                log.info("[情侣任务] 自动识别完成 userId={} taskId={} content={} completeType={}",
                        userId, task.getId(), task.getContent(), completeType);

                // 免领取任务：完成即发放积分
                if (task.getClaimRequired() != null && task.getClaimRequired() == 0
                        && task.getPoints() != null && task.getPoints() > 0) {
                    int claimed = userTaskMapper.markClaimed(userId.longValue(), task.getId(),
                            periodKey, task.getPoints());
                    if (claimed == 1) {
                        pointsService.addPoints(userId.longValue(), task.getPoints(),
                                PointsConstant.CHANGE_TYPE_TASK, PointsConstant.REF_TASK,
                                task.getId(), "完成任务：" + task.getContent());
                    }
                }
            }
            if (changed) {
                cacheSupport.evictUserList(userId);
            }
        } catch (Exception e) {
            // 任务识别失败不影响主业务（发消息 / 传照片 / 建计划）
            log.error("[情侣任务] 自动识别失败 userId={} completeType={}", userId, completeType, e);
        }
    }
}
