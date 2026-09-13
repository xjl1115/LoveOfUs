package com.example.lovemap.makeover.ai;

import com.example.lovemap.makeover.constant.MakeoverConstant;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 化妆建议 Prompt 构造 + Qwen-VL 调用
 * <p>
 * 使用 ai.dashscope.makeover.* 配置的专用多模态 ChatModel（默认 qwen-vl-max）。
 */
@Slf4j
@Component
public class MakeoverPromptBuilder {

    private final ChatModel dashscopeChatModel;

    public MakeoverPromptBuilder(@Qualifier("dashscopeMakeoverChatModel") ChatModel dashscopeChatModel) {
        this.dashscopeChatModel = dashscopeChatModel;
    }

    /**
     * 调用 Qwen-VL 分析自拍照，输出严格 JSON 文本
     *
     * @param originalUrl 自拍照 URL
     * @param sceneCode   场景编码
     * @param sceneText   用户额外描述（可空）
     * @return AI 返回的文本（应是严格 JSON）
     */
    public String analyze(String originalUrl, String sceneCode, String sceneText) {
        // 关键：DashScope MultiModalConversation 要求 messages[0].role = "user"。
        // 把 SYSTEM_PROMPT 合并进 UserMessage 文本前缀，只发一条 user 消息，避免 400。
        String combinedPrompt = MakeoverConstant.SYSTEM_PROMPT
                + "\n\n"
                + buildUserPrompt(sceneCode, sceneText);

        List<TextContent> textContents = List.of(TextContent.from(combinedPrompt));
        List<ImageContent> imageContents = List.of(ImageContent.from(originalUrl));
        List<dev.langchain4j.data.message.Content> contents = new java.util.ArrayList<>();
        contents.addAll(textContents);
        contents.addAll(imageContents);

        UserMessage userMessage = UserMessage.from(contents);
        log.info("[Makeover-AI] 调用 Qwen-VL, scene={}, imageUrl={}", sceneCode, originalUrl);

        ChatResponse response;
        try {
            response = dashscopeChatModel.chat(userMessage);
        } catch (Exception e) {
            log.error("[Makeover-AI] Qwen-VL 调用失败", e);
            throw new RuntimeException("AI 化妆建议分析失败: " + e.getMessage(), e);
        }

        String aiText = response.aiMessage() != null ? response.aiMessage().text() : "";
        log.info("[Makeover-AI] Qwen-VL 返回文本长度: {}", aiText.length());
        return aiText;
    }

    /**
     * 构造用户提示词
     * <p>
     * 设计原则（情侣 App 场景）：
     * <ol>
     *   <li>先定调（场合 / 关系 / 用户偏好），再让 AI 给具体方案，避免"通用模板"答案；</li>
     *   <li>显式标注目标用户肤色（亚洲），约束调色方向，避免"美式古铜 / 欧美冷白"误判；</li>
     *   <li>强调"亲密可见距离"——情侣 App 的核心场景是约会见面，妆容细节会被近距离看到，
     *       因此睫毛根根分明、唇色不斑驳、眉形自然等微距指标比"远看气场"更重要；</li>
     *   <li>把场景氛围拆成"光感 / 配色 / 质感"三个维度，引导 AI 给出可执行的搭配，</li>
     *       而不是一个抽象的"风格"标签。
     * </ol>
     */
    String buildUserPrompt(String sceneCode, String sceneText) {
        String sceneName = MakeoverConstant.SCENE_NAME.getOrDefault(sceneCode, sceneCode);
        String extra = (sceneText == null || sceneText.isBlank()) ? "无" : sceneText;
        // 香水香调族提示：让 AI 严格选家族之一，避免输出"清爽甜美"等模糊词
        String perfumeHint = buildPerfumeHint(sceneCode);
        // 场景氛围标签（光感/配色/质感）：把抽象的"风格"拆成 AI 真正能控制的三个维度
        String moodHint = buildSceneMoodHint(sceneCode);
        return """
                【场景】%s
                【用户额外要求】%s（若空则忽略，仅参考场景默认建议）
                %s
                %s

                【分析视角】
                - 主体：自拍用户，肤色不限（请按图片实际判断），脸型/肤质/五官/发型作为分析基础，请基于图片客观观察，不要编造没有的特征。
                - 关系：这是一个情侣 App 内的功能，最终建议会被分享给伴侣看，"会面第一印象"是关键 KPI。
                - 距离：约会/通勤等场景默认是 0.5~1.5m 的近距离接触，妆容细节（睫毛分明度、唇色边界、底妆服帖度）会被对方看到，
                  请避免"舞台浓妆"或"舞台式修容"，更注重自然精致的"伪素颜"质感。

                【输出要求】
                1. 严格按 SYSTEM 给的 JSON 结构输出，禁止任何额外文字、Markdown 代码块或前后缀。
                2. 香水香调族（perfume.family）必须从【该场景建议香水香调族】清单中选一个，禁止自创如"清新甜美"等模糊词。
                3. 香水产品名（perfume.product）若不确定请留空，<b>不要编造不存在的品牌或型号</b>；
                   若给出产品名请优先选国内主流渠道可买到的产品（祖玛珑、Diptyque、Chanel、Dior、Tom Ford、Byredo、Le Labo、阿玛尼、爱马仕等）。
                4. 服装 items 字段请列出 3~5 件具体单品（如"米白色羊绒圆领针织衫+高腰阔腿西裤+裸色尖头短靴+小号链条腋下包"），不要只给一个抽象标签。
                5. tips 字段至少给出 3 条可执行动作（如"眼线沿睫毛根部画、不超过眼尾 3mm"），避免"建议""可以"等模糊词。
                """.formatted(sceneName, extra, perfumeHint, moodHint);
    }

    /**
     * 场景氛围标签：把"光感 / 配色 / 质感"三个维度的具体指引拼成 prompt 段落。
     * <p>
     * 为什么要拆三个维度：wanx 改造图 / Qwen 文本生成对"光感"和"配色"是分开响应的，
     * 直接说"自然精致"AI 经常理解成"无风格"；明确给光感和配色，AI 才能给出有方向感的建议。
     */
    private String buildSceneMoodHint(String sceneCode) {
        // key 与 MakeoverConstant.SCENE_NAME 一致；null 兜底为 other
        String code = (sceneCode == null) ? "other" : sceneCode;
        String[][] triples = {
                // code, 光感, 主色, 材质/质感
                {"date",    "暖黄灯光 / 烛光 / 街灯霓虹",
                          "玫瑰粉、酒红、香槟金、裸色、奶咖",
                          "缎面、丝绒、薄纱、细闪高光"},
                {"commute", "自然日光 / 室内柔光",
                          "奶茶色、雾霾蓝、燕麦白、烟灰粉",
                          "哑光、雾面、精纺羊毛、棉麻"},
                {"party",   "强聚光 / 闪光灯 / 紫红灯光",
                          "电光蓝、亮片金、丝绒红、烟熏紫",
                          "亮片、缎面、金属光、漆皮"},
                {"travel",  "户外阳光 / 海风天光",
                          "白、海军蓝、亚麻色、橘子汽水",
                          "棉麻、亚麻、防水麂皮、轻薄牛仔"},
                {"wedding", "柔光 + 暖白棚拍",
                          "香槟金、奶白、淡粉、灰蓝",
                          "蕾丝、真丝、欧根纱、珍珠光"},
                {"daily",   "自然日光 / 室内暖光",
                          "燕麦白、奶咖、雾霾绿、浅灰",
                          "针织、棉、软牛仔、麂皮绒"},
                {"other",   "自然日光",
                          "基础黑、白、灰、米",
                          "哑光、棉、毛呢"}
        };
        for (String[] row : triples) {
            if (row[0].equals(code)) {
                return "【场景氛围指引】\n"
                        + "- 光感：" + row[1] + "\n"
                        + "- 配色：" + row[2] + "\n"
                        + "- 质感：" + row[3];
            }
        }
        // 兜底：理论上不会到这里（SCENE_NAME 兜底为 other）
        return "【场景氛围指引】自然日光，基础中性配色，哑光棉麻质感";
    }

    /**
     * 构造香水香调族提示词。把"该场景建议的香调族清单"以中英文形式喂给 AI，
     * 约束 perfume.family 的取值范围，避免输出"清甜"等模糊分类。
     */
    private String buildPerfumeHint(String sceneCode) {
        java.util.List<String> families = MakeoverConstant.SCENE_PERFUME_FAMILIES
                .getOrDefault(sceneCode, MakeoverConstant.SCENE_PERFUME_FAMILIES.get("other"));
        StringBuilder sb = new StringBuilder("【该场景建议香水香调族】");
        for (String code : families) {
            String cn = MakeoverConstant.PERFUME_FAMILY_NAME.getOrDefault(code, code);
            sb.append(cn).append("(").append(code).append(") / ");
        }
        sb.setLength(sb.length() - 3);
        return sb.toString();
    }
}