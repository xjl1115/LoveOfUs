package com.example.lovemap.makeover.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MakeoverResultParser 单元测试
 */
class MakeoverResultParserTest {

    private final MakeoverResultParser parser = new MakeoverResultParser();

    @Test
    void parse_normalJson() {
        String json = """
                {
                  "faceFeatures": {
                    "faceShape": "椭圆脸",
                    "skinTone": "暖一白",
                    "eyeShape": "杏眼",
                    "lipShape": "薄唇",
                    "hairLength": "中长发"
                  },
                  "suggestions": {
                    "makeup": {"base": "清透奶油肌", "eye": "哑光大地色", "lip": "玫瑰豆沙色", "brow": "标准眉"},
                    "hair": {"style": "低马尾", "color": "黑茶色"},
                    "accessory": ["细链锁骨链"],
                    "outfit": {"style": "法式温柔", "items": ["米白色针织开衫"], "colorTips": "莫兰迪色系"},
                    "tips": ["侧脸更显瘦"]
                  }
                }
                """;
        MakeoverResultParser.ParseResult r = parser.parse(json);
        assertEquals("椭圆脸", r.faceFeatures().get("faceShape"));
        assertNotNull(r.suggestions().get("makeup"));
        assertEquals("低马尾", ((java.util.Map<?, ?>) r.suggestions().get("hair")).get("style"));
    }

    @Test
    void parse_missingFields() {
        // 缺失部分字段，应使用空字符串兜底
        String json = "{\"faceFeatures\":{\"faceShape\":\"圆脸\"},\"suggestions\":{\"makeup\":{}}}";
        MakeoverResultParser.ParseResult r = parser.parse(json);
        assertEquals("圆脸", r.faceFeatures().get("faceShape"));
        assertEquals("", r.faceFeatures().get("eyeShape"));
        assertEquals("", r.faceFeatures().get("hairLength"));
    }

    @Test
    void parse_extraFields() {
        // 多余字段应被 Jackson 自动忽略
        String json = "{\"faceFeatures\":{\"faceShape\":\"圆脸\",\"extra\":\"ignore me\"},"
                + "\"suggestions\":{\"makeup\":{},\"extra\":[]}}";
        MakeoverResultParser.ParseResult r = parser.parse(json);
        assertEquals("圆脸", r.faceFeatures().get("faceShape"));
        assertTrue(r.suggestions().containsKey("makeup"));
    }

    @Test
    void parse_truncatedJson() {
        // 截断 JSON 缺少右括号，应抛 IllegalArgumentException
        String truncated = "{\"faceFeatures\":{\"faceShape\":\"圆脸\"}";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(truncated));
    }

    @Test
    void parse_markdownWrapped() {
        // AI 偶尔返回 markdown 包裹
        String wrapped = "```json\n{\"faceFeatures\":{\"faceShape\":\"圆脸\"},\"suggestions\":{\"makeup\":{\"base\":\"裸妆\"}}}\n```";
        MakeoverResultParser.ParseResult r = parser.parse(wrapped);
        assertEquals("圆脸", r.faceFeatures().get("faceShape"));
    }

    @Test
    void parse_emptyText() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }
}