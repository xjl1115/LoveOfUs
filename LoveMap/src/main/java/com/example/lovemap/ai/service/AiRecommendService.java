package com.example.lovemap.ai.service;

import com.example.lovemap.common.constant.AiConstant;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * AI 推荐服务
 * <p>
 * 复用现有的 LangChain4j ChatModel，为约会计划、心愿清单等场景生成推荐内容。
 * 本服务不调用工具、不持久化会话，仅做一次性内容生成。
 * <p>
 * 性能/安全防护：
 * <ul>
 *   <li>缓存 —— 同 (key 后缀) 1 小时内直接复用 Redis 命中，避免重复调用 LLM。</li>
 *   <li>限流 —— 每用户每日最多 N 次 AI 生成，超额返回固定降级文本，不抛异常。</li>
 * </ul>
 */
@Slf4j
@Service
public class AiRecommendService {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final StringRedisTemplate redisTemplate;

    /** 上海时区，与 application.yml system-prompt 及 TimeRangeTool 保持一致 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter WEEKDAY_FMT = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA);

    public AiRecommendService(@Qualifier("dashscopeChatModel") ObjectProvider<ChatModel> chatModelProvider,
                              StringRedisTemplate redisTemplate) {
        this.chatModelProvider = chatModelProvider;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成约会计划推荐
     *
     * @param userHint 用户偏好/约束描述
     * @return AI 生成的推荐文本
     */
    public String recommendDatePlan(String userHint) {
        LocalDate today = LocalDate.now(ZONE);
        String systemPrompt = "你是一位贴心的恋爱约会规划师。请根据以下信息，为一对情侣生成 3-5 个浪漫的约会计划建议。" +
                "每个建议包含标题、适合场景、大致预算和一句话亮点。" +
                "要求：只输出建议内容，不要反问；使用中文；适当使用 emoji，单条回复不超过 6 个。" +
                "时间基准（必须严格遵守）：今天是 " + today.format(DATE_FMT) +
                "（" + today.format(WEEKDAY_FMT) + "，Asia/Shanghai）。" +
                "用户可能用相对时间描述档期，参考定义：今天/明天/本周末（最近的周六、周日）/下周末（下周六、下周日）/本月/下月。" +
                "你必须基于今天的日期推算真实日期，严禁使用训练数据中的历史日期；" +
                "输出中的 date 字段必须为 YYYY-MM-DD 格式，且不得早于今天。";
        return generate(systemPrompt, userHint, null, AiConstant.COPY_CACHE_TTL_SECONDS);
    }

    /**
     * 生成心愿清单推荐
     *
     * @param userHint 用户偏好/约束描述
     * @param existingTitles 用户已有心愿标题，用于避免重复推荐；可为空
     * @return AI 生成的推荐文本
     */
    public String recommendWishlist(String userHint, List<String> existingTitles) {
        LocalDate today = LocalDate.now(ZONE);
        String systemPrompt = "你是一位懂情侣心愿的恋爱助手。请根据以下信息，为一对情侣生成 5-8 个值得一起完成的心愿清单项目。" +
                "每个项目包含标题和简短说明。" +
                "要求：只输出内容，不要反问；使用中文；适当使用 emoji，单条回复不超过 6 个。" +
                "时间基准（必须严格遵守）：今天是 " + today.format(DATE_FMT) +
                "（" + today.format(WEEKDAY_FMT) + "，Asia/Shanghai）。" +
                "用户可能用相对时间描述期望完成时间，参考定义：今天/明天/本周末（最近的周六、周日）/下周末（下周六、下周日）/本月/下月/今年。" +
                "你必须基于今天的日期推算真实日期，严禁使用训练数据中的历史日期；" +
                "输出中的 targetDate 字段必须为 YYYY-MM-DD 格式，且不得早于今天。" +
                "若用户消息中给出了「已有心愿」列表，严禁重复推荐其中已存在或高度相似的项目。";
        String hint = userHint == null ? "" : userHint.trim();
        if (existingTitles != null && !existingTitles.isEmpty()) {
            hint = hint + "\n\n【已有心愿（请勿重复推荐）】\n- " + String.join("\n- ", existingTitles);
        }
        return generate(systemPrompt, hint, null, AiConstant.COPY_CACHE_TTL_SECONDS);
    }

    /**
     * 生成纪念日文案（朋友圈/卡片/短句风格）
     *
     * @param userHint 场景描述，如「在一起 1000 天 / 情人节 / 老婆生日，风格温柔」
     * @return AI 生成的文案
     */
    public String generateAnniversaryCopy(String userHint) {
        LocalDate today = LocalDate.now(ZONE);
        String systemPrompt = "你是一位浪漫而真诚的文案师。请根据用户提供的纪念日场景，生成 3-5 条适合发朋友圈、纪念卡片或私信的中文文案。" +
                "要求：\n" +
                "1. 风格温暖、真诚、有画面感，避免过度堆砌形容词；\n" +
                "2. 每条文案长度控制在 20-60 字之间，不要超过 80 字；\n" +
                "3. 不要使用'我爱你'、'永远在一起'这类陈词滥调；\n" +
                "4. 每条用换行分隔，前面用 1️⃣2️⃣3️⃣ 编号（纯 emoji 数字，无需方括号）；\n" +
                "5. 不要反问，不要解释，只输出文案本体。\n" +
                "时间基准（必须严格遵守）：今天是 " + today.format(DATE_FMT) +
                "（" + today.format(WEEKDAY_FMT) + "，Asia/Shanghai）。" +
                "若文案涉及具体日期、相识或相恋天数、节气与节日，必须基于今天推算，严禁使用训练数据中的历史日期。";
        return generate(systemPrompt, userHint, null, AiConstant.COPY_CACHE_TTL_SECONDS);
    }

    /**
     * 带缓存 key 哈希的纪念日文案生成。
     * <p>
     * 调用方传入的 cacheKeySuffix 通常由 (anniversaryId + style + extra) 计算得出；
     * 1 小时内同一组合直接复用缓存，避免对 LLM 的重复调用。
     */
    public String generateAnniversaryCopy(String userHint, String cacheKeySuffix) {
        LocalDate today = LocalDate.now(ZONE);
        String systemPrompt = "你是一位浪漫而真诚的文案师。请根据用户提供的纪念日场景，生成 3-5 条适合发朋友圈、纪念卡片或私信的中文文案。" +
                "要求：\n" +
                "1. 风格温暖、真诚、有画面感，避免过度堆砌形容词；\n" +
                "2. 每条文案长度控制在 20-60 字之间，不要超过 80 字；\n" +
                "3. 不要使用'我爱你'、'永远在一起'这类陈词滥调；\n" +
                "4. 每条用换行分隔，前面用 1️⃣2️⃣3️⃣ 编号（纯 emoji 数字，无需方括号）；\n" +
                "5. 不要反问，不要解释，只输出文案本体。\n" +
                "时间基准（必须严格遵守）：今天是 " + today.format(DATE_FMT) +
                "（" + today.format(WEEKDAY_FMT) + "，Asia/Shanghai）。" +
                "若文案涉及具体日期、相识或相恋天数、节气与节日，必须基于今天推算，严禁使用训练数据中的历史日期。";
        String cacheKey = AiConstant.ANNIVERSARY_COPY_CACHE_PREFIX + cacheKeySuffix;
        return generate(systemPrompt, userHint, cacheKey, AiConstant.COPY_CACHE_TTL_SECONDS);
    }

    /**
     * 基于偏好与预算生成礼物推荐
     *
     * @param userHint 礼物场景，如「生日 / 预算 200-500 / 女生 / 喜欢文艺手工」
     * @return AI 生成的礼物清单
     */
    public String recommendGiftByPreference(String userHint) {
        String systemPrompt = "你是一位贴心的礼物顾问。请根据用户给出的对象特征和预算，推荐 5-8 个高性价比、有心意、容易买到的礼物。" +
                "每个推荐包含：礼物名称、推荐理由（一句话）、参考价位。\n" +
                "要求：\n" +
                "1. 优先考虑'有心意 > 贵重'，注重情感价值；\n" +
                "2. 控制总价在用户指定预算范围内；\n" +
                "3. 每个推荐用换行分隔，前面用 1️⃣2️⃣3️⃣ 编号；\n" +
                "4. 礼物要具体可买，不要泛泛而谈（如'一本书'应具体到书名或类型）；\n" +
                "5. 只输出礼物本体，不要反问。";
        return generate(systemPrompt, userHint, null, AiConstant.GIFT_CACHE_TTL_SECONDS);
    }

    /**
     * 基于现有心愿清单生成礼物推荐
     *
     * @param contextHint 上下文（预算/节日/补充偏好），可空
     * @param wishlistTitles 当前心愿清单中已收集到的心愿标题（多行拼接传入）
     * @return AI 基于心愿清单的礼物建议
     */
    public String recommendGiftFromWishlist(String contextHint, String wishlistTitles) {
        String systemPrompt = "你是恋爱礼物顾问。用户已经维护了一份心愿清单，请优先从清单中挑选" +
                "最符合当前场景的 3-5 项作为礼物建议。\n" +
                "每个建议包含：礼物名称（来自清单）、为什么适合、推荐购买时机/价位。\n" +
                "要求：\n" +
                "1. 必须从下方心愿清单中挑选，不要凭空编造新礼物；\n" +
                "2. 编号 1️⃣2️⃣3️⃣ 用换行分隔；\n" +
                "3. 如果清单为空或与场景不匹配，礼貌告知用户'清单里暂时没有合适的，建议补充几个心愿'；\n" +
                "4. 不要反问，只输出建议本体。";
        String userPrompt = (wishlistTitles == null || wishlistTitles.isBlank())
                ? "心愿清单为空。"
                : "【心愿清单】\n" + wishlistTitles + "\n\n【场景/补充】\n"
                        + (contextHint == null || contextHint.isBlank() ? "无补充信息" : contextHint);
        // 心愿清单随 group 变化比较慢，缓存 1h
        String cacheKey = AiConstant.GIFT_WISHLIST_CACHE_PREFIX
                + sha256(userPrompt).substring(0, 16);
        return generate(systemPrompt, userPrompt, cacheKey, AiConstant.GIFT_CACHE_TTL_SECONDS);
    }

    // ==================== 反馈回路支持 ====================

    /**
     * 用户点击「采纳」文案时记录反馈，供后续 Prompt 优化分析。
     *
     * @param cacheKeySuffix 命中缓存时使用的 key 后缀（anniversaryId+style+extra）
     * @param userId         当前用户 ID
     * @param accepted       是否采纳
     */
    public void recordCopyFeedback(String cacheKeySuffix, Long userId, boolean accepted) {
        // 直接转发给反馈服务，避免 AI 推荐层反向依赖 ai.service.feedback 包
        // 由调用方在 AiCopyFeedbackService 注入后实现；此处保留空实现以兼容旧调用。
        log.debug("[AI-RECOMMEND] recordCopyFeedback cacheKeySuffix={}, userId={}, accepted={}（业务侧自行落库）",
                cacheKeySuffix, userId, accepted);
    }

    // ==================== 内部：缓存 + 限流 + 调用 LLM ====================

    /**
     * 通用生成：先查 Redis 缓存 → 检查日限流 → 调用 LLM → 写回缓存。
     *
     * @param systemPrompt   系统提示词
     * @param userHint       用户输入
     * @param cacheKey       缓存 key；null 表示不缓存
     * @param ttlSeconds     缓存有效期（秒）
     * @return 生成的文本
     */
    private String generate(String systemPrompt, String userHint, String cacheKey, long ttlSeconds) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("[AI-RECOMMEND] ChatModel 不可用，AI 服务未启用或未配置 API Key");
            return "AI 推荐功能暂未开启，请检查 AI 配置后再试。";
        }

        String text = userHint == null ? "" : userHint.trim();
        if (text.isEmpty()) {
            text = "没有特别要求，请自由发挥。";
        }

        // 1. 缓存命中
        if (cacheKey != null) {
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null && !cached.isBlank()) {
                    log.info("[AI-RECOMMEND] cache hit key={}", cacheKey);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("[AI-RECOMMEND] 读取缓存失败，降级调用 LLM key={}", cacheKey, e);
            }
        }

        // 2. 限流（按自然日计数）
        if (!incrementDailyUsage()) {
            log.warn("[AI-RECOMMEND] 用户当日调用次数超限，降级返回固定文本");
            return "今天 AI 推荐已用完啦，明天再来试试吧～（每日上限 " + AiConstant.DAILY_MAX_INVOCATIONS + " 次）";
        }

        // 3. 调用 LLM
        try {
            ChatRequestParameters params = ChatRequestParameters.builder().build();
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(systemPrompt), UserMessage.from(text)))
                    .parameters(params)
                    .build();

            dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(request);
            String result = response.aiMessage() == null ? null : response.aiMessage().text();
            result = result == null || result.isBlank()
                    ? "抱歉，AI 暂时没有生成推荐内容，请换种描述再试。"
                    : result;

            // 4. 写回缓存（仅成功结果）
            if (cacheKey != null && !result.startsWith("抱歉") && !result.startsWith("今天")) {
                try {
                    redisTemplate.opsForValue().set(cacheKey, result, ttlSeconds, TimeUnit.SECONDS);
                    log.info("[AI-RECOMMEND] cache write key={} ttl={}s", cacheKey, ttlSeconds);
                } catch (Exception e) {
                    log.warn("[AI-RECOMMEND] 写缓存失败 key={}", cacheKey, e);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[AI-RECOMMEND] 生成推荐内容失败", e);
            return "AI 推荐生成失败，请稍后重试。";
        }
    }

    /**
     * 用户当日调用次数 +1，超过上限返回 false。
     * <p>
     * Redis key：ai:rate:{userId}:{yyyyMMdd}，自然日零点自动过期。
     * 由于 AiUserContext 中并未持久化 userId 注入，本方法采用「调用方不感知 userId」的方案，
     * 通过 AiUserContext.peekUserId() 取；若取不到则不计数（极端情况——单元测试 / 工具内调用）。
     */
    private boolean incrementDailyUsage() {
        try {
            Long userId = com.example.lovemap.ai.context.AiUserContext.peekUserId();
            if (userId == null) {
                // 上下文缺失：放行（不会无限增长，因为上层一般会注入 userId）
                return true;
            }
            String key = AiConstant.USER_RATE_LIMIT_PREFIX + userId + ":" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 首次写入，附上 48h TTL 防止跨天后脏数据残留（48h 已覆盖任何时区偏差）
                redisTemplate.expire(key, 48, TimeUnit.HOURS);
            }
            if (count != null && count > AiConstant.DAILY_MAX_INVOCATIONS) {
                return false;
            }
            return true;
        } catch (Exception e) {
            // 限流依赖 Redis；Redis 故障时放行，避免阻塞正常 AI 体验
            log.warn("[AI-RECOMMEND] 限流检查异常，本次放行", e);
            return true;
        }
    }

    /**
     * 取字符串 SHA-256 前 16 个十六进制字符。空字符串返回 "0"。
     */
    public static String sha256(String s) {
        if (s == null || s.isEmpty()) return "0";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
