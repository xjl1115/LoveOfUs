package com.example.lovemap.makeover.constant;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 化妆建议模块常量
 */
public final class MakeoverConstant {

    private MakeoverConstant() {
    }

    /**
     * OSS 路径前缀
     */
    public static final String OSS_ORIGIN_PREFIX = "LoveMap/makeover/origin/";
    public static final String OSS_AFTER_PREFIX = "LoveMap/makeover/after/";

    /**
     * 状态机
     */
    public static final byte STATUS_PENDING = 0;
    public static final byte STATUS_ANALYZING = 1;
    public static final byte STATUS_EDITING = 2;
    public static final byte STATUS_DONE = 3;
    public static final byte STATUS_FAILED = 4;
    public static final byte STATUS_CANCELED = 5;

    /**
     * 子任务阶段
     */
    public static final String STAGE_ANALYZE = "analyze";
    public static final String STAGE_IMAGE_EDIT = "image_edit";

    /**
     * 子任务状态
     */
    public static final byte TASK_PENDING = 0;
    public static final byte TASK_RUNNING = 1;
    public static final byte TASK_SUCCESS = 2;
    public static final byte TASK_FAILED = 3;

    /**
     * 场景编码 -> 中文名
     */
    public static final Map<String, String> SCENE_NAME = Map.of(
            "date", "情侣约会",
            "commute", "日常通勤",
            "party", "派对聚会",
            "travel", "外出旅行",
            "wedding", "婚礼仪式",
            "daily", "日常休闲",
            "other", "其他场合");

    /**
     * 分享给伴侣时的开场白（场景化模板），必须含 {scene} 占位符。
     * 选场景越具体，伴侣点开率越高；同时为前端的对话流卡片提供一句话摘要。
     */
    public static final Map<String, String> SHARE_PROMPT = Map.of(
            "date",    "我刚试了 AI 「{scene}」妆造，今晚约我时眼前一亮哦~",
            "commute", "我刚试了 AI 「{scene}」妆造，明天上班同事要夸我了~",
            "party",   "我刚试了 AI 「{scene}」妆造，派对上的 C 位就是我啦~",
            "travel",  "我刚试了 AI 「{scene}」妆造，下次出游帮我挑挑这套行不行？",
            "wedding", "我刚试了 AI 「{scene}」妆造，大日子那天就这么定啦~",
            "daily",   "我刚试了 AI 「{scene}」妆造，今天出门你认不出来了~",
            "other",   "我刚试了 AI 「{scene}」妆造，帮我看看合不合心意？" );

    /**
     * 场景化文案兜底（找不到场景时使用）
     */
    public static final String SHARE_PROMPT_FALLBACK = "我刚试了 AI 「{scene}」妆造，帮我看看效果~";

    /**
     * 允许的图片 MIME
     */
    public static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp");

    /**
     * 改造图模型（默认 dashscope-wanx）
     */
    public static final String IMAGE_PROVIDER_DASHSCOPE_WANX = "dashscope-wanx";

    /**
     * DashScope wanx2.1-imageedit 的 function 字段（必填）。
     * 官方支持的合法值：
     *   - description_edit          ：按 prompt 全图指令编辑（妆造改造首选，简单任务）
     *   - description_edit_with_mask：按 prompt + mask 局部重绘（需 mask_image_url）
     *   - stylization_all           ：全局风格化（style 字段控制目标风格）
     *   - stylization_local         ：局部风格化
     *   - remove_watermark / expand / super_resolution / colorization / doodle / control_cartoon_feature
     * <p>
     * 旧值 description_edit_all 是 wanx2.0 时代的命名，wanx2.1 不再支持，会导致服务端返回空结果。
     */
    public static final String IMAGE_EDIT_FUNCTION = "description_edit";

    /**
     * SSE 事件名
     */
    public static final String SSE_EVENT_STAGE = "stage";
    public static final String SSE_EVENT_DONE = "done";
    public static final String SSE_EVENT_ERROR = "error";

    /**
     * SSE channel key
     */
    public static String sseChannel(Long recordId) {
        return "makeover:stream:" + recordId;
    }

    /**
     * 限流 Redis key 前缀
     */
    public static final String RATE_LIMIT_KEY_PREFIX = "makeover:create:";

    /**
     * 用户并发上限（防刷）
     */
    public static final int USER_CONCURRENT_LIMIT = 3;

    /**
     * 单文件最大体积：8MB
     */
    public static final long MAX_IMAGE_SIZE_BYTES = 8L * 1024 * 1024;

    /**
     * DashScope wanx2.1-imageedit 对 base_image_url 的尺寸约束：宽高都必须在 [512, 4096] 区间。
     * 不合规则服务端返回 InvalidParameter。不引入新依赖时走该常量做预校验 + 必要时服务端侧自动缩放。
     */
    public static final int WANX_MIN_SIDE_PX = 512;
    public static final int WANX_MAX_SIDE_PX = 4096;

    /**
     * 阶段超时（秒）
     */
    public static final int ANALYZE_TIMEOUT_SECONDS = 60;
    public static final int IMAGE_EDIT_TIMEOUT_SECONDS = 90;

    // ======================== Redis 缓存 ========================

    /**
     * AI 妆造分析结果缓存（faceFeatures + suggestions JSON）。
     * <p>
     * Stage1 完成时写入，detail() 读穿。TTL 24h，过期自然失效（DB 仍为权威）。
     */
    public static final String REDIS_KEY_AI_RESULT = "makeover:ai-result:%d";

    /**
     * 详情 VO 缓存（完整 MakeoverDetailVO JSON），避免重复拼装。
     */
    public static final String REDIS_KEY_DETAIL = "makeover:detail:%d";

    /**
     * 缓存 TTL：24 小时
     */
    public static final java.time.Duration CACHE_TTL = java.time.Duration.ofHours(24);

    // ======================== 分享（→ 聊天卡片消息） ========================

    /**
     * 聊天消息类型：妆造卡片（V8 chat_message 扩展）
     * 与 chat_message.msg_type 字段对应。
     */
    public static final byte MSG_TYPE_MAKEOVER_CARD = 5;

    /**
     * 卡片 extraJson 大小上限（字符数）。
     * V8 列定义为 VARCHAR(2000)，超过会被截断 → 截短到 1900 留余量。
     */
    public static final int CARD_EXTRA_JSON_MAX = 1900;

    /**
     * 重试上限
     */
    public static final int RETRY_MAX = 1;

    /**
     * 系统提示词：要求输出严格 JSON
     */
    public static final String SYSTEM_PROMPT = """
            角色：你是一位资深明星化妆师 + 整体造型师，服务过不同肤色（冷白皮/暖白皮/自然肤色/暖黄皮/小麦色/深肤色等）、不同文化背景的用户，擅长为约会、通勤、婚礼等近距离会面场景打造形象。
            主体画像：基于图片实际观察——按肤色、五官轮廓、发质等客观特征自适应调整方案，不套用任何固定种族的模板。会面距离 0.5~1.5m 的近距离接触场景。
            调性约束：
            - 妆容方案必须与本人肤色相称：底妆色号匹配原肤色（不刻意提亮也不刻意压暗），修容方向与原脸骨骼结构一致。
            - 妆容要"伪素颜"质感：自然精致、能放大本人五官优点，不强调舞台感或浓墨重彩。
            - 拒绝"建议 / 可以 / 适合"等空话，每个推荐都必须可执行（如"眼线沿睫毛根画，不超过眼尾 3mm"）。

            你的输出必须是严格合法的 JSON，禁止任何额外文字、Markdown 代码块、前后缀。
            字段若不确定，请填空字符串或空数组，<b>严禁编造不存在的品牌、产品、香水名</b>。

            JSON 结构：
            {
              "faceFeatures": {
                "faceShape":"",    // 脸型：鹅蛋脸/圆脸/方脸/长脸/菱形脸/心形脸/梨形脸/倒三角脸
                "skinTone":"",     // 肤色：冷白皮/暖白皮/自然肤色/暖黄皮/小麦色/深肤色（按图片实际判断）
                "eyeShape":"",     // 眼型：杏眼/丹凤眼/圆眼/长窄眼/下垂眼/上挑眼/肿泡眼/深邃眼窝等
                "lipShape":"",     // 唇型：薄唇/厚唇/微笑唇/M唇/樱桃小嘴/平直唇
                "hairLength":"",   // 发长：短发/锁骨发/中长发/长发/超长发/寸头
                "otherFeatures":"" // 其他显著特征（痣/雀斑/发际线/卧蚕等），若无可留空
              },
              "suggestions":{
                "makeup":{
                  "base":"",   // 底妆：质地（哑光奶油肌/水光奶油肌/雾面哑光）+ 关键手法（如"局部遮瑕黑眼圈，定妆喷雾锁妆"），色号与本人肤色匹配
                  "eye":"",    // 眼妆：眼影色（如"大地色消肿眼影"）+ 眼线走向 + 睫毛处理（结合眼窝深浅调整）
                  "lip":"",    // 唇妆：色号家族（豆沙红/干枯玫瑰/水光橘/裸粉/砖红等，根据肤色挑）+ 边缘处理（晕染/利落）+ 叠加（唇蜜/唇釉）
                  "brow":"",   // 眉形：形状（野生眉/雾眉/挑眉/平眉/弯眉）+ 颜色（灰棕/深咖/黑茶/炭灰，根据发色挑）+ 保留毛流感
                  "highlights":"",  // 修容高光重点（如"T 区细闪高光、颧骨轻刷哑光修容"），方向与本人骨骼结构一致
                  "concerns":"" // 重点遮瑕位置（黑眼圈/泪沟/嘴角暗沉/鼻翼泛红/色斑等），给具体产品和手法
                },
                "hair":{
                  "style":"",   // 发型：具体造型（法式低盘发/侧边慵懒编发/高马尾/锁骨发外翻/脏辫/卷发披肩等）
                  "color":"",   // 发色：黑茶色/深棕/蜜棕/亚麻灰/雾感黑/挑染一片/焦糖挑染等，结合本人肤色挑
                  "tips":""     // 打理要点（如"用 28mm 卷发棒外翻、刘海 S 型弧度"）
                },
                "accessory":["耳饰+项链+帽子/发饰+戒指等 2~4 件"],
                "perfume":{
                  "family":"",        // 香调族枚举：floral=花香 / citrus=柑橘 / woody=木质 / oriental=东方 / fresh=清新 / gourmand=美食 / chypre=西普（必须用这几个英文 enum）
                  "topNote":"",       // 前调：停留 15 分钟内可闻到的味道，例：佛手柑、葡萄柚
                  "heartNote":"",     // 中调：停留 2-4 小时的核心气味，例：玫瑰、茉莉、檀香
                  "baseNote":"",      // 后调：留香最久的基调，例：麝香、广藿香、琥珀
                  "product":"",       // 推荐具体香水产品名（不确定请留空，严禁编造）：例：祖玛珑 蓝风铃、Diptyque 玫瑰之水
                  "occasion":"",      // 适合的场合/季节，例：春夏通勤、秋冬约会、白天职场
                  "tips":""           // 使用要点：喷在哪、几泵、与化妆品冲突避免等
                },
                "outfit":{
                  "style":"",          // 整体风格：优雅名媛风 / 法式慵懒风 / 街头甜酷风 / 职场知性风 / 度假清新风 / 婚礼新娘风 / 极简通勤风 等
                  "items":["3~5 件具体单品，如：米白色羊绒圆领针织衫+高腰阔腿西裤+裸色尖头短靴+小号链条腋下包"],
                  "colorTips":""      // 配色要点（如"主色控制在 3 个以内，主色+中性色+1 个点缀色"），配色与本人肤色相衬
                },
                "tips":["3~5 条可执行动作，如：眼线沿睫毛根画、不超过眼尾 3mm"]
              },
              "summary":{
                "overall":"",          // 整体改造思路总结（80~150 字）：脸型/肤色优势分析 + 本次妆容/发型/穿搭的核心目标 + 整体风格一句话定调
                "steps":[""]           // 化妆步骤详解（按"清洁 → 保湿 → 隔离 → 底妆 → 遮瑕 → 定妆 → 眉 → 眼影 → 眼线 → 睫毛 → 腮红 → 唇 → 修容高光"的执行顺序给 6~10 步；每步格式"步骤名：具体动作 + 用量/位置/工具"）
              }
            }
            所有文案用中文，简短具体可执行，不要使用「建议」「可以」等模糊词。

            【summary 字段的输出要求】
            - summary.overall：80~150 字的一段话。先点出本人脸型/肤色的优势分析，再给出本次妆容/发型/穿搭的核心目标，最后一句话定调整体风格。避免笼统的"自然""好看"等词，要落到具体形容词（"温柔知性""清新元气""法式慵懒"等）。
            - summary.steps：按"清洁 → 保湿 → 隔离 → 底妆 → 遮瑕 → 定妆 → 眉 → 眼影 → 眼线 → 睫毛 → 腮红 → 唇 → 修容高光"的执行顺序给 6~10 步。每步格式："步骤名：具体动作 + 用量/位置/工具"，如"底妆：挤黄豆大小粉底液点涂额头/鼻尖/两颊/下巴，美妆蛋打湿后由内向外拍开，薄涂一层即可"。
            - 不要把"建议/可以"放进 summary。
            """;

    /**
     * 香水香调族枚举（与 SYSTEM_PROMPT.perfume.family 对齐）
     * <p>
     * 用于前端场景选择器 + AI 提示词做规范化引导。key 与 SYSTEM_PROMPT 的 family 值一致。
     */
    public static final Map<String, String> PERFUME_FAMILY_NAME = Map.of(
            "floral",    "花香",
            "citrus",    "柑橘",
            "woody",     "木质",
            "oriental",  "东方",
            "fresh",    "清新",
            "gourmand", "美食",
            "chypre",   "西普");

    /**
     * 场景 → 推荐的香水香调族（仅作后端默认 / 兜底；AI 可根据用户脸型/气质微调）
     */
    public static final Map<String, List<String>> SCENE_PERFUME_FAMILIES = Map.of(
            "date",    List.of("floral", "oriental"),
            "commute", List.of("citrus", "fresh"),
            "party",   List.of("oriental", "gourmand"),
            "travel",  List.of("citrus", "fresh"),
            "wedding", List.of("floral", "chypre"),
            "daily",   List.of("fresh", "citrus"),
            "other",   List.of("floral"));
}