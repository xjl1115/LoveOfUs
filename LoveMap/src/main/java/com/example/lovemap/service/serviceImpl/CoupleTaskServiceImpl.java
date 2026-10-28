package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.PointsConstant;
import com.example.lovemap.mapper.CoupleTaskMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.mapper.UserTaskMapper;
import com.example.lovemap.model.dto.CoupleTaskClaimDTO;
import com.example.lovemap.model.dto.CoupleTaskSaveDTO;
import com.example.lovemap.model.entity.CoupleTask;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.entity.UserTask;
import com.example.lovemap.model.vo.CoupleTaskVO;
import com.example.lovemap.service.CoupleTaskService;
import com.example.lovemap.service.PointsService;
import com.example.lovemap.service.support.CoupleTaskCacheSupport;
import com.example.lovemap.utils.BusinessDateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 情侣任务实现
 *
 * 状态读写策略：
 *   - 任务定义：couple:task:def 缓存
 *   - 用户列表：couple:task:list:{userId} 缓存，完成 / 领取后失效
 *   - 当日行懒加载：查询时按「用户 × 任务 × 周期键」拼装状态，
 *     缺失行按 status=0 输出，不写库，避免「日活 × 任务数」的空行膨胀
 *   - 领取积分：先条件 UPDATE 置为「已领取」，受影响行数=1 才发积分（并发安全）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoupleTaskServiceImpl implements CoupleTaskService {

    private final CoupleTaskMapper taskMapper;
    private final UserTaskMapper userTaskMapper;
    private final UserMapper userMapper;
    private final PointsService pointsService;
    private final CoupleTaskCacheSupport cacheSupport;
    private final BusinessDateUtils businessDateUtils;

    @Override
    public Result<List<CoupleTaskVO>> listForUser(Integer userId) {
        requireUser(userId);

        List<CoupleTaskVO> cached = cacheSupport.readUserList(userId);
        if (cached != null) {
            return Result.success(cached);
        }

        List<CoupleTask> tasks = loadEnabledTasks();
        List<CoupleTaskVO> vos = assembleUserList(userId.longValue(), tasks);
        cacheSupport.writeUserList(userId, vos);
        return Result.success(vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<CoupleTaskVO> claim(Integer userId, CoupleTaskClaimDTO dto) {
        User user = requireUser(userId);
        CoupleTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != PointsConstant.TASK_ENABLED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "任务已下架");
        }
        if (task.getPoints() == null || task.getPoints() <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该任务未配置积分奖励");
        }
        if (task.getClaimRequired() != null && task.getClaimRequired() == 0) {
            return Result.badRequest("该任务完成后积分自动发放，无需领取");
        }

        String periodKey = businessDateUtils.currentPeriodKey(task.getPeriodType());

        // 条件更新：仅「已完成待领取」才能置为「已领取」，返回受影响行数
        int rows = userTaskMapper.markClaimed(user.getId(), task.getId(), periodKey, task.getPoints());
        if (rows != 1) {
            return Result.badRequest("任务尚未完成，或积分已领取");
        }

        // 置为已领取成功后才发放积分；同事务，失败则整体回滚
        int balance = pointsService.addPoints(user.getId(), task.getPoints(),
                PointsConstant.CHANGE_TYPE_TASK, PointsConstant.REF_TASK,
                task.getId(), "完成任务：" + task.getContent());

        cacheSupport.evictUserList(userId);

        CoupleTaskVO vo = toVO(task, periodKey);
        vo.setTaskStatus(PointsConstant.USER_TASK_CLAIMED);
        vo.setCompleted(true);
        vo.setClaimable(false);
        vo.setPointsAwarded(task.getPoints());

        log.info("[情侣任务] 领取积分 userId={} taskId={} points={} balance={}",
                user.getId(), task.getId(), task.getPoints(), balance);
        return Result.success("已领取 " + task.getPoints() + " 积分", vo);
    }

    @Override
    public Result<List<CoupleTaskVO>> listAll() {
        List<CoupleTask> all = taskMapper.selectAll();
        if (all == null || all.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<CoupleTaskVO> vos = new ArrayList<>(all.size());
        for (CoupleTask t : all) {
            vos.add(toVO(t, null));
        }
        return Result.success(vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> save(CoupleTaskSaveDTO dto) {
        CoupleTask t = new CoupleTask();
        t.setId(dto.getId());
        t.setContent(dto.getContent().trim());
        t.setDescription(dto.getDescription());
        t.setIcon(dto.getIcon());
        t.setPoints(dto.getPoints());
        t.setStatus(dto.getStatus());
        t.setCompleteType(StringUtils.hasText(dto.getCompleteType())
                ? dto.getCompleteType() : PointsConstant.COMPLETE_MANUAL);
        t.setPeriodType(StringUtils.hasText(dto.getPeriodType())
                ? dto.getPeriodType() : PointsConstant.PERIOD_DAILY);
        t.setClaimRequired(dto.getClaimRequired() != null ? dto.getClaimRequired() : 1);
        t.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);

        if (dto.getId() == null) {
            taskMapper.insert(t);
        } else {
            if (taskMapper.selectById(dto.getId()) == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
            }
            taskMapper.update(t);
        }
        evictAllCache();
        return Result.success(t.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(Long id) {
        if (taskMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        taskMapper.deleteById(id);
        evictAllCache();
        return Result.success();
    }

    // ==================== 私有 ====================

    /**
     * 拼装用户任务列表：任务定义 + 该用户在当前周期的状态行
     */
    private List<CoupleTaskVO> assembleUserList(Long userId, List<CoupleTask> tasks) {
        // 1) 计算每个任务的周期键，并收集去重后的键集合
        Map<Long, String> taskPeriodKeyMap = new HashMap<>();
        Set<String> periodKeys = new HashSet<>();
        for (CoupleTask t : tasks) {
            String periodKey = businessDateUtils.currentPeriodKey(t.getPeriodType());
            taskPeriodKeyMap.put(t.getId(), periodKey);
            periodKeys.add(periodKey);
        }

        // 2) 一次性查询该用户在这些周期下的状态行
        Map<String, UserTask> statusMap = new HashMap<>();
        if (!periodKeys.isEmpty()) {
            List<UserTask> rows = userTaskMapper.selectByUserAndPeriodKeys(
                    userId, new ArrayList<>(periodKeys));
            if (rows != null) {
                for (UserTask row : rows) {
                    statusMap.put(statusKey(row.getTaskId(), row.getPeriodKey()), row);
                }
            }
        }

        // 3) 缺失行按「未完成」输出，不写库
        List<CoupleTaskVO> vos = new ArrayList<>(tasks.size());
        for (CoupleTask t : tasks) {
            String periodKey = taskPeriodKeyMap.get(t.getId());
            CoupleTaskVO vo = toVO(t, periodKey);
            UserTask row = statusMap.get(statusKey(t.getId(), periodKey));
            if (row == null) {
                vo.setTaskStatus(PointsConstant.USER_TASK_INCOMPLETE);
                vo.setCompleted(false);
            } else {
                vo.setTaskStatus(row.getStatus());
                vo.setCompleted(row.getStatus() != null
                        && row.getStatus() != PointsConstant.USER_TASK_INCOMPLETE);
                vo.setCompletedAt(row.getCompletedAt());
                vo.setClaimedAt(row.getClaimedAt());
                vo.setPointsAwarded(row.getPointsAwarded());
            }
            vo.setClaimable(vo.getTaskStatus() != null
                    && vo.getTaskStatus() == PointsConstant.USER_TASK_COMPLETED
                    && vo.getClaimRequired() != null && vo.getClaimRequired() == 1);
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 读取已启用任务定义（带缓存）
     */
    private List<CoupleTask> loadEnabledTasks() {
        List<CoupleTask> tasks = cacheSupport.readEnabledTasks();
        if (tasks == null) {
            tasks = taskMapper.selectEnabled();
            if (tasks == null) {
                tasks = Collections.emptyList();
            }
            cacheSupport.writeEnabledTasks(tasks);
        }
        return tasks;
    }

    private CoupleTaskVO toVO(CoupleTask t, String periodKey) {
        CoupleTaskVO vo = new CoupleTaskVO();
        vo.setId(t.getId());
        vo.setContent(t.getContent());
        vo.setDescription(t.getDescription());
        vo.setIcon(t.getIcon());
        vo.setPoints(t.getPoints());
        vo.setStatus(t.getStatus());
        vo.setSortOrder(t.getSortOrder());
        vo.setCompleteType(t.getCompleteType());
        vo.setPeriodType(t.getPeriodType());
        vo.setClaimRequired(t.getClaimRequired());
        vo.setPeriodKey(periodKey);
        vo.setTaskStatus(PointsConstant.USER_TASK_INCOMPLETE);
        vo.setCompleted(false);
        vo.setClaimable(false);
        return vo;
    }

    private String statusKey(Long taskId, String periodKey) {
        return taskId + "#" + periodKey;
    }

    private User requireUser(Integer userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        return u;
    }

    /**
     * 任务定义变更：失效定义缓存与全部用户列表缓存
     */
    private void evictAllCache() {
        cacheSupport.evictEnabledTasks();
        cacheSupport.evictAllUserLists();
    }
}
