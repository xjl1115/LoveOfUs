package com.example.lovemap.makeover.ai;

import com.example.lovemap.makeover.constant.MakeoverConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MakeoverPromptBuilder 单元测试
 */
class MakeoverPromptBuilderTest {

    @Test
    void systemPrompt_notContainsMarkdownFence() {
        // 系统提示词不应包含 Markdown 代码块标记，避免 LLM 误以为代码示例
        assertFalse(MakeoverConstant.SYSTEM_PROMPT.contains("```"));
    }

    @Test
    void systemPrompt_containsJsonStructure() {
        assertTrue(MakeoverConstant.SYSTEM_PROMPT.contains("faceFeatures"));
        assertTrue(MakeoverConstant.SYSTEM_PROMPT.contains("suggestions"));
    }

    @Test
    void buildUserPrompt_includesScene() {
        MakeoverPromptBuilder builder = new MakeoverPromptBuilder(null);
        String prompt = builder.buildUserPrompt("date", "希望温柔一点");
        assertTrue(prompt.contains("情侣约会"));
        assertTrue(prompt.contains("希望温柔一点"));
    }

    @Test
    void buildUserPrompt_unknownSceneFallsBack() {
        MakeoverPromptBuilder builder = new MakeoverPromptBuilder(null);
        String prompt = builder.buildUserPrompt("unknown_scene", null);
        assertTrue(prompt.contains("unknown_scene")); // 未知场景时回显原值
        assertTrue(prompt.contains("无")); // 无额外要求时填"无"
    }

    @Test
    void buildUserPrompt_emptyExtraShowsWu() {
        MakeoverPromptBuilder builder = new MakeoverPromptBuilder(null);
        String prompt = builder.buildUserPrompt("commute", "");
        assertTrue(prompt.contains("无"));
    }

    @Test
    void sceneNameMap_containsExpectedCodes() {
        assertEquals("情侣约会", MakeoverConstant.SCENE_NAME.get("date"));
        assertEquals("日常通勤", MakeoverConstant.SCENE_NAME.get("commute"));
        assertEquals("派对聚会", MakeoverConstant.SCENE_NAME.get("party"));
    }
}