package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.UserStatsVO;
import com.example.lovemap.service.UserService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户/情侣统计 AI 工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatsTool {

    private final UserService userService;

    /**
     * 获取用户统计：照片数、相册数、城市数、在一起天数、城市分布
     */
    @Tool("获取当前用户的整体统计：照片总数、相册总数、去过的城市数、在一起天数、按省份的照片分布。")
    public Map<String, Object> getUserStats() {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] getUserStats userId={}", userId);
        try {
            Result<UserStatsVO> result = userService.getUserStats(userId.intValue());
            if (result == null || result.getData() == null) {
                return Map.of("error", "未获取到统计数据");
            }
            UserStatsVO s = result.getData();
            Map<String, Object> map = new HashMap<>();
            map.put("photoCount", s.getPhotoCount());
            map.put("albumCount", s.getAlbumCount());
            map.put("cityCount", s.getCityCount());
            map.put("daysTogether", s.getDaysTogether());

            List<Map<String, Object>> cities = new ArrayList<>();
            if (s.getCities() != null) {
                for (UserStatsVO.ProvinceStatVO p : s.getCities()) {
                    Map<String, Object> cm = new HashMap<>();
                    cm.put("name", p.getName());
                    cm.put("count", p.getCount());
                    cm.put("latestTakenDate", p.getTakenDate() != null ? p.getTakenDate().toString() : null);
                    cities.add(cm);
                }
            }
            map.put("provinceDistribution", cities);
            return map;
        } catch (Exception e) {
            log.error("[AI-TOOL] getUserStats 失败", e);
            return Map.of("error", "查询失败：" + e.getMessage());
        }
    }
}