-- AI 纪念日文案反馈表
-- 用于记录用户对 AI 生成文案的采纳 / 拒绝,作为反哺 Prompt 优化的数据源

CREATE TABLE IF NOT EXISTS ai_copy_feedback (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL COMMENT '反馈用户 ID',
    anniversary_id    BIGINT       DEFAULT NULL COMMENT '关联纪念日 ID,可空(批量场景)',
    style             VARCHAR(32)  NOT NULL DEFAULT 'romantic' COMMENT '文案风格:romantic/casual/humor/poetic',
    cache_key_suffix  VARCHAR(128) NOT NULL COMMENT '与 Redis 缓存 key 后缀一致,便于关联',
    feedback          TINYINT      NOT NULL DEFAULT 0 COMMENT '反馈类型:0-未反馈 1-采纳 2-拒绝',
    copy_preview      VARCHAR(500) DEFAULT NULL COMMENT '采纳 / 拒绝时的文案摘要(便于人工复核)',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_cache (user_id, cache_key_suffix),
    INDEX idx_anniversary (anniversary_id),
    INDEX idx_feedback (feedback, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 纪念日文案反馈';