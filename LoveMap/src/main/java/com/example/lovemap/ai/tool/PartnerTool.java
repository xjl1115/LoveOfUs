package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.User;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 伴侣信息 AI 工具（P0）
 * <p>
 * 让 AI 回答时能说出"宝贝/亲爱的"而不是"用户您好"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerTool {

    private final UserMapper userMapper;

    /**
     * 获取当前用户与伴侣的基本信息：昵称、头像、绑定日期、城市、在一起天数
     */
    @Tool("获取当前用户与其伴侣（已绑定的情况下）的昵称、头像、所在城市、在一起天数、城市分布。AI 称呼对方时用此工具得到昵称。")
    public Map<String, Object> getPartnerInfo() {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] getPartnerInfo userId={}", userId);
        try {
            User me = userMapper.selectById(userId.intValue());
            if (me == null) {
                return Map.of("error", "用户不存在");
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("meId", me.getId());
            map.put("meNickname", me.getNickname());
            map.put("meAvatar", me.getAvatarUrl());
            map.put("isBound", Boolean.TRUE.equals(me.getIsBound()));
            map.put("groupId", me.getGroupId());

            // 在一起天数
            if (me.getRelationshipStart() != null) {
                long days = ChronoUnit.DAYS.between(me.getRelationshipStart(), LocalDate.now());
                map.put("relationshipStart", me.getRelationshipStart().toString());
                map.put("daysTogether", Math.max(days, 0));
            }

            if (!isBoundTrue(me.getIsBound()) || me.getPartnerId() == null) {
                map.put("bound", false);
                map.put("hint", "当前用户尚未绑定伴侣");
                return map;
            }
            User partner = userMapper.selectById(me.getPartnerId().intValue());
            if (partner == null) {
                map.put("bound", true);
                map.put("hint", "已绑定但找不到伴侣用户记录");
                return map;
            }
            map.put("bound", true);
            map.put("partnerId", partner.getId());
            map.put("partnerNickname", partner.getNickname());
            map.put("partnerAvatar", partner.getAvatarUrl());

            return map;
        } catch (Exception e) {
            log.error("[AI-TOOL] getPartnerInfo 失败", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "查询失败：" + e.getMessage());
            return err;
        }
    }

    /**
     * User.isBound 在数据库为 TINYINT(0/1)，MyBatis 反射到 Integer 字段，
     * 因此这里用 Integer 1 / Boolean.TRUE 两种情况都能正确识别"已绑定"。
     */
    private boolean isBoundTrue(Integer v) {
        if (v == null) return false;
        return v == 1 || v == 2; // 1=true; 容忍未来改用2=bound
    }
}