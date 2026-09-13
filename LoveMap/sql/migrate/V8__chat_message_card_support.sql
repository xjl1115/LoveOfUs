-- ============================================================
-- V8：聊天消息支持卡片消息（image_url + extra_json）
--
-- 背景：AI 化妆建议模块（V7）需要把"原图 + 改造图 + 妆造建议"作为一张
-- 卡片消息发给伴侣。复用 chat_message 表，扩展两个可选列即可：
--   image_url   改造图 OSS URL（卡片主视觉），nullable 兼容老消息
--   extra_json  卡片附加数据 JSON（max 2000 字符），如 faceFeatures + suggestions 摘要
--
-- msg_type 取值扩展：
--   1 = 文本（已有）
--   5 = 妆造卡片（新增）
-- ============================================================

ALTER TABLE chat_message
    ADD COLUMN image_url VARCHAR(512) NULL                COMMENT '卡片主图 URL（msg_type=5 时为改造图）' AFTER content,
    ADD COLUMN extra_json VARCHAR(2000) NULL              COMMENT '卡片附加 JSON（msg_type>=5 时存额外数据）' AFTER msg_type;

-- 索引：按图 URL 检索（一般用不上，但保持完整）
CREATE INDEX idx_image_url ON chat_message (image_url);