package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.ai.service.AiRecommendService;
import com.example.lovemap.common.Result;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.WishlistItemVO;
import com.example.lovemap.service.WishlistItemService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 礼物推荐 AI 工具
 * <p>
 * 提供 3 个工具方法：
 * <ul>
 *   <li>recommendGiftsByPreference   —— 基于「场景 + 对象特征 + 预算」推荐礼物</li>
 *   <li>recommendGiftsFromWishlist   —— 基于心愿清单 + 场景挑选礼物</li>
 *   <li>recommendGiftsForAnniversary —— 围绕「某个纪念日」推荐礼物（结合预算/心愿清单）</li>
 * </ul>
 * 所有方法只读、不写数据库、不引入 confirm 流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GiftRecommendTool {

    private final AiRecommendService aiRecommendService;
    private final WishlistItemService wishlistItemService;
    private final UserMapper userMapper;

    /**
     * 基于偏好与预算推荐礼物（无清单依赖）。
     * <p>
     * 典型用法：「老婆生日 / 预算 500 以内 / 喜欢文艺手工 / 想突出陪伴感」。
     */
    @Tool("基于场景、对象特征和预算推荐 5-8 个礼物。"
            + "scene 可选：birthday/anniversary/valentine/christmas/daily/no-occasion；"
            + "recipientGender 可选 female/male；budgetRMB 传整数预算；"
            + "preference 传对象偏好，如「喜欢文艺手工/讨厌香水」；extra 传其他补充。")
    public Map<String, Object> recommendGiftsByPreference(
            @P("场景 birthday/anniversary/valentine/christmas/daily/no-occasion，可空") String scene,
            @P("收礼人性别 female/male，可空") String recipientGender,
            @P("预算（人民币 元），可空") Integer budgetRMB,
            @P("对象偏好，如「喜欢手工/讨厌香水」，可空") String preference,
            @P("其他补充，如「突出陪伴感/需要便携」，可空") String extra) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] recommendGiftsByPreference userId={}, scene={}, recipientGender={}, budgetRMB={}",
                userId, scene, recipientGender, budgetRMB);

        StringBuilder hint = new StringBuilder();
        hint.append("【场景】").append(normalizeScene(scene)).append('\n');
        hint.append("【收礼人性别】").append(normalizeGender(recipientGender)).append('\n');
        if (budgetRMB != null && budgetRMB > 0) {
            hint.append("【预算】≤ ").append(budgetRMB).append(" 元（不含惊喜仪式/餐饮）\n");
        }
        if (preference != null && !preference.isBlank()) {
            hint.append("【对象偏好】").append(preference.trim()).append('\n');
        }
        if (extra != null && !extra.isBlank()) {
            hint.append("【补充】").append(extra.trim()).append('\n');
        }
        // 自动注入伴侣昵称（如有）便于 LLM 称谓
        User me = userMapper.selectById(userId.intValue());
        if (me != null && me.getPartnerId() != null) {
            User partner = userMapper.selectById(me.getPartnerId().intValue());
            if (partner != null && partner.getNickname() != null) {
                hint.append("【对方昵称】").append(partner.getNickname()).append('\n');
            }
        }

        try {
            String content = aiRecommendService.recommendGiftByPreference(hint.toString());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "OK");
            resp.put("mode", "by_preference");
            resp.put("scene", normalizeScene(scene));
            resp.put("recipientGender", normalizeGender(recipientGender));
            resp.put("budgetRMB", budgetRMB);
            resp.put("gifts", content);
            return resp;
        } catch (Exception e) {
            log.error("[AI-TOOL] recommendGiftsByPreference 失败", e);
            return Map.of("error", "礼物推荐失败：" + e.getMessage());
        }
    }

    /**
     * 基于已有心愿清单 + 场景推荐礼物（要求用户已维护过心愿）。
     */
    @Tool("从当前用户的「心愿清单」中挑选 3-5 项最贴合当前场景的礼物建议。"
            + "scene 可选：birthday/anniversary/valentine/christmas/daily/no-occasion；"
            + "budgetRMB 可选；extra 可传补充偏好。"
            + "若心愿清单为空会返回 error，提示用户先去补充心愿。")
    public Map<String, Object> recommendGiftsFromWishlist(
            @P("场景 birthday/anniversary/valentine/christmas/daily/no-occasion，可空") String scene,
            @P("预算（人民币 元），可空") Integer budgetRMB,
            @P("补充偏好，可空") String extra) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] recommendGiftsFromWishlist userId={}, scene={}, budgetRMB={}",
                userId, scene, budgetRMB);

        Result<List<WishlistItemVO>> r = wishlistItemService.listWishlistItems(userId.intValue());
        List<WishlistItemVO> items = (r == null || r.getData() == null) ? List.of() : r.getData();
        if (items.isEmpty()) {
            return Map.of(
                    "error", "心愿清单为空，请先在 App 中添加几个心愿，再让 AI 帮你挑选礼物",
                    "status", "EMPTY_WISHLIST");
        }

        StringBuilder wishlist = new StringBuilder();
        for (WishlistItemVO vo : items) {
            if (vo.getTitle() == null || vo.getTitle().isBlank()) continue;
            wishlist.append("- ").append(vo.getTitle());
            if (vo.getCategory() != null && !vo.getCategory().isBlank()) {
                wishlist.append("（分类：").append(vo.getCategory()).append("）");
            }
            if (vo.getDescription() != null && !vo.getDescription().isBlank()) {
                wishlist.append("：").append(vo.getDescription());
            }
            wishlist.append('\n');
        }

        StringBuilder context = new StringBuilder();
        context.append("场景：").append(normalizeScene(scene)).append('\n');
        if (budgetRMB != null && budgetRMB > 0) {
            context.append("预算：≤ ").append(budgetRMB).append(" 元\n");
        }
        if (extra != null && !extra.isBlank()) {
            context.append("补充：").append(extra.trim());
        }

        try {
            String content = aiRecommendService.recommendGiftFromWishlist(
                    context.toString(), wishlist.toString());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "OK");
            resp.put("mode", "from_wishlist");
            resp.put("wishlistCount", items.size());
            resp.put("scene", normalizeScene(scene));
            resp.put("budgetRMB", budgetRMB);
            resp.put("gifts", content);
            return resp;
        } catch (Exception e) {
            log.error("[AI-TOOL] recommendGiftsFromWishlist 失败", e);
            return Map.of("error", "礼物推荐失败：" + e.getMessage());
        }
    }

    /**
     * 围绕「某个纪念日」推荐礼物：自动结合场景 + 心愿清单 + 预算。
     * <p>
     * 心愿清单为空时自动降级为「基于偏好」模式。
     */
    @Tool("围绕某个纪念日推荐礼物：自动结合场景、心愿清单与预算。"
            + "anniversaryName 传纪念日名称（模糊匹配）；budgetRMB 可选。"
            + "若心愿清单为空，会自动降级为基于偏好的推荐模式。")
    public Map<String, Object> recommendGiftsForAnniversary(
            @P("纪念日名称（模糊匹配现有纪念日）") String anniversaryName,
            @P("预算（人民币 元），可空") Integer budgetRMB,
            @P("补充偏好，可空") String extra) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] recommendGiftsForAnniversary userId={}, name='{}', budgetRMB={}",
                userId, anniversaryName, budgetRMB);
        if (anniversaryName == null || anniversaryName.isBlank()) {
            return Map.of("error", "anniversaryName 不能为空");
        }

        // 取心愿清单
        Result<List<WishlistItemVO>> wr = wishlistItemService.listWishlistItems(userId.intValue());
        List<WishlistItemVO> wishlist = (wr == null || wr.getData() == null) ? List.of() : wr.getData();
        StringBuilder wishlistText = new StringBuilder();
        for (WishlistItemVO vo : wishlist) {
            if (vo.getTitle() == null || vo.getTitle().isBlank()) continue;
            wishlistText.append("- ").append(vo.getTitle());
            if (vo.getCategory() != null && !vo.getCategory().isBlank()) {
                wishlistText.append("（").append(vo.getCategory()).append("）");
            }
            wishlistText.append('\n');
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "OK");
        resp.put("mode", "for_anniversary");
        resp.put("anniversaryName", anniversaryName.trim());
        resp.put("budgetRMB", budgetRMB);
        resp.put("wishlistCount", wishlist.size());

        StringBuilder context = new StringBuilder();
        context.append("围绕纪念日「").append(anniversaryName.trim()).append("」挑选礼物。\n");
        if (budgetRMB != null && budgetRMB > 0) {
            context.append("预算：≤ ").append(budgetRMB).append(" 元\n");
        }
        if (extra != null && !extra.isBlank()) {
            context.append("补充：").append(extra.trim()).append('\n');
        }

        try {
            String content;
            if (wishlist.isEmpty()) {
                resp.put("degraded", "by_preference");
                content = aiRecommendService.recommendGiftByPreference(context.toString());
            } else {
                content = aiRecommendService.recommendGiftFromWishlist(context.toString(), wishlistText.toString());
            }
            resp.put("gifts", content);
            return resp;
        } catch (Exception e) {
            log.error("[AI-TOOL] recommendGiftsForAnniversary 失败", e);
            return Map.of("error", "礼物推荐失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private String normalizeScene(String s) {
        if (s == null || s.isBlank()) return "no-occasion（无特定场景）";
        switch (s.trim().toLowerCase()) {
            case "birthday": return "birthday（生日）";
            case "anniversary": return "anniversary（纪念日）";
            case "valentine": return "valentine（情人节）";
            case "christmas": return "christmas（圣诞节）";
            case "daily": return "daily（日常/无理由）";
            case "no-occasion": return "no-occasion（无特定场景）";
            default: return s.trim();
        }
    }

    private String normalizeGender(String g) {
        if (g == null || g.isBlank()) return "未指定";
        String v = g.trim().toLowerCase();
        if ("female".equals(v) || "f".equals(v) || "女".equals(v)) return "女性";
        if ("male".equals(v) || "m".equals(v) || "男".equals(v)) return "男性";
        return g.trim();
    }
}