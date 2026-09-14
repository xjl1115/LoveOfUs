package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.mapper.ThingsCompletionMapper;
import com.example.lovemap.mapper.ThingsMapper;
import com.example.lovemap.mapper.ThingsUrlMapper;
import com.example.lovemap.mapper.ThingsUrlRelMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.dto.ThingsAchieveDTO;
import com.example.lovemap.model.dto.ThingsPhotoUploadDTO;
import com.example.lovemap.model.entity.Things;
import com.example.lovemap.model.entity.ThingsCompletion;
import com.example.lovemap.model.entity.ThingsUrl;
import com.example.lovemap.model.entity.ThingsUrlRel;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.ThingsListVO;
import com.example.lovemap.model.vo.ThingsPhotoVO;
import com.example.lovemap.model.vo.ThingsStatsVO;
import com.example.lovemap.service.ThingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 情侣必做 100 件事服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThingsServiceImpl implements ThingsService {

    private final ThingsMapper thingsMapper;
    private final ThingsCompletionMapper completionMapper;
    private final ThingsUrlMapper thingsUrlMapper;
    private final ThingsUrlRelMapper urlRelMapper;
    private final UserMapper userMapper;

    /**
     * 列出 100 件事（按 group 合并完成状态 + 照片数）
     */
    @Override
    public Result<List<ThingsListVO>> listThings(Integer userId) {
        User user = requireUser(userId);
        Long gid = user.getGroupId();

        List<Things> all = thingsMapper.selectAll();
        if (all.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 查询完成记录（情侣组数据，或未绑定情侣时本人的个人记录）
        List<ThingsCompletion> completions = completionMapper.selectByGroupOrUser(gid, userId.longValue());
        Map<Long, ThingsCompletion> completionMap = completions.stream()
                .collect(Collectors.toMap(ThingsCompletion::getThingId, c -> c, (a, b) -> a));

        // 批量统计每项的照片数（一次查询）
        Map<Long, Integer> photoCountMap = countPhotosByThingForGroupOrUser(gid, userId);

        List<ThingsListVO> result = all.stream().map(t -> {
            ThingsListVO vo = new ThingsListVO();
            vo.setId(t.getId());
            vo.setOrder(t.getId().intValue());
            vo.setTitle(t.getName());
            vo.setDescription(t.getDescription());
            // icon 直接透传数据库字段（可能为空），由前端按 title 推断 emoji
            vo.setIcon(t.getIcon());
            vo.setCoverEmoji(t.getIcon());
            vo.setCategory(t.getCategory());
            ThingsCompletion c = completionMap.get(t.getId());
            if (c != null) {
                vo.setAchievedAt(c.getCompletedAt());
                vo.setAchievedNote(c.getNote());
            }
            vo.setPhotoCount(photoCountMap.getOrDefault(t.getId(), 0));
            return vo;
        }).collect(Collectors.toList());

        return Result.success(result);
    }

    @Override
    public Result<ThingsStatsVO> getStats(Integer userId) {
        User user = requireUser(userId);
        ThingsStatsVO vo = new ThingsStatsVO();
        long total = thingsMapper.countAll();
        vo.setTotal(total);
        vo.setAchieved(completionMapper.countAchievedByGroupOrUser(user.getGroupId(), userId.longValue()));
        vo.setRate(total > 0 ? vo.getAchieved().doubleValue() / total : 0d);
        return Result.success(vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> achieve(Integer userId, ThingsAchieveDTO dto) {
        User user = requireUser(userId);
        Things thing = thingsMapper.selectById(dto.getThingId());
        if (thing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "事项不存在");
        }

        ThingsCompletion existing = completionMapper.selectByThingAndGroupOrUser(
                dto.getThingId(), user.getGroupId(), userId.longValue());
        boolean wantCancel = Boolean.TRUE.equals(dto.getCancel());

        if (wantCancel) {
            // 取消完成
            if (existing != null) {
                completionMapper.deleteByThingAndGroupOrUser(dto.getThingId(), user.getGroupId(), userId.longValue());
            }
            thingsMapper.updateCompleted(dto.getThingId(), 0);
            return Result.success();
        }

        // 标记完成
        if (existing == null) {
            ThingsCompletion c = new ThingsCompletion();
            c.setThingId(dto.getThingId());
            c.setGroupId(user.getGroupId());
            c.setCompletedBy(user.getId());
            c.setCompletedAt(LocalDateTime.now());
            c.setNote(dto.getNote());
            completionMapper.insert(c);
        } else {
            existing.setNote(dto.getNote());
            existing.setCompletedBy(user.getId());
            existing.setCompletedAt(LocalDateTime.now());
            // 已存在则只改 note / completedBy / completed_at
            completionMapper.deleteByThingAndGroupOrUser(dto.getThingId(), user.getGroupId(), userId.longValue());
            completionMapper.insert(existing);
        }

        // 同步标记 things 表完成状态
        thingsMapper.updateCompleted(dto.getThingId(), 1);

        // 关联附带照片
        if (dto.getPhotoIds() != null && !dto.getPhotoIds().isEmpty()) {
            for (Long urlId : dto.getPhotoIds()) {
                ThingsUrl url = thingsUrlMapper.selectById(urlId);
                if (url == null) continue;
                ThingsUrlRel rel = new ThingsUrlRel();
                rel.setThingId(dto.getThingId());
                rel.setUrlId(urlId);
                rel.setGroupId(user.getGroupId());
                rel.setUploadedBy(user.getId());
                rel.setCreatedAt(LocalDateTime.now());
                urlRelMapper.batchInsert(Collections.singletonList(rel));
            }
        }
        return Result.success();
    }

    @Override
    public Result<List<ThingsPhotoVO>> listPhotos(Integer userId, Long thingId) {
        User user = requireUser(userId);
        Things thing = thingsMapper.selectById(thingId);
        if (thing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "事项不存在");
        }
        List<ThingsUrlRel> rels = urlRelMapper.selectByThingIdAndGroupOrUser(
                thingId, user.getGroupId(), userId.longValue());
        if (rels.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        // 批量查 URL
        Map<Long, String> urlMap = new HashMap<>();
        for (ThingsUrlRel rel : rels) {
            ThingsUrl u = thingsUrlMapper.selectById(rel.getUrlId());
            if (u != null) urlMap.put(rel.getUrlId(), u.getUrl());
        }
        List<ThingsPhotoVO> vos = rels.stream()
                .map(r -> {
                    ThingsPhotoVO vo = new ThingsPhotoVO();
                    vo.setId(r.getUrlId());
                    vo.setUrl(urlMap.getOrDefault(r.getUrlId(), ""));
                    return vo;
                })
                .filter(v -> v.getUrl() != null && !v.getUrl().isEmpty())
                .collect(Collectors.toList());
        return Result.success(vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ThingsPhotoVO> uploadPhoto(Integer userId, ThingsPhotoUploadDTO dto) {
        User user = requireUser(userId);
        Things thing = thingsMapper.selectById(dto.getThingId());
        if (thing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "事项不存在");
        }

        // 落库 url
        ThingsUrl url = new ThingsUrl();
        url.setUrl(dto.getUrl());
        thingsUrlMapper.insert(url);

        // 关联
        ThingsUrlRel rel = new ThingsUrlRel();
        rel.setThingId(dto.getThingId());
        rel.setUrlId(url.getId());
        rel.setGroupId(user.getGroupId());
        rel.setUploadedBy(user.getId());
        rel.setCreatedAt(LocalDateTime.now());
        urlRelMapper.batchInsert(Collections.singletonList(rel));

        ThingsPhotoVO vo = new ThingsPhotoVO();
        vo.setId(url.getId());
        vo.setUrl(url.getUrl());
        return Result.success(vo);
    }

    // ==================== 私有辅助 ====================

    private User requireUser(Integer userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        return u;
    }

    /**
     * 批量统计每项的照片数（情侣组 + 未绑定情侣时本人的个人记录）。
     * 性能：100 行直接 IN 查 + 内存 group，避免 N+1。
     */
    private Map<Long, Integer> countPhotosByThingForGroupOrUser(Long groupId, Integer userId) {
        List<ThingsUrlRel> all = urlRelMapper.selectByGroupOrUser(groupId, userId.longValue());
        Map<Long, Integer> map = new HashMap<>();
        for (ThingsUrlRel r : all) {
            map.merge(r.getThingId(), 1, Integer::sum);
        }
        return map;
    }
}
