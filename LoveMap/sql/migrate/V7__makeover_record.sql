-- AI 化妆建议记录表（一张自拍照对应一条记录）
CREATE TABLE IF NOT EXISTS makeover_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         INT          NOT NULL                COMMENT '所属用户',
    partner_id      INT          NULL                    COMMENT '对方用户（已绑定时冗余）',
    original_url    VARCHAR(512) NOT NULL                COMMENT '原图 OSS 访问 URL',
    original_key    VARCHAR(256) NOT NULL                COMMENT 'OSS 对象键',
    scene_code      VARCHAR(32)  NOT NULL                COMMENT '场景编码：date/commute/party/travel/wedding/daily/other',
    scene_text      VARCHAR(255) NULL                    COMMENT '场景自由文本（用户额外描述）',
    face_features   JSON         NULL                    COMMENT 'AI 返回的脸型/肤质特征 JSON',
    suggestions     JSON         NOT NULL                COMMENT '妆造/配饰/服装建议 JSON',
    after_url       VARCHAR(512) NULL                    COMMENT '改造效果图 OSS URL（生成成功后回填）',
    after_key       VARCHAR(256) NULL                    COMMENT '改造图 OSS 对象键',
    status          TINYINT      NOT NULL DEFAULT 0      COMMENT '0=待处理 1=分析中 2=改造图生成中 3=已完成 4=失败 5=已取消',
    error_message   VARCHAR(500) NULL                    COMMENT '失败原因',
    cost_ms         BIGINT       NULL                    COMMENT '总耗时（毫秒）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '0=未删 1=已删',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status, deleted),
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 化妆建议记录';

-- 异步任务子表：拆分「文本分析」「出图」两个阶段，便于重试
CREATE TABLE IF NOT EXISTS makeover_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    record_id       BIGINT       NOT NULL,
    stage           VARCHAR(16)  NOT NULL                COMMENT 'analyze / image_edit',
    status          TINYINT      NOT NULL DEFAULT 0      COMMENT '0=待执行 1=执行中 2=成功 3=失败',
    retry_count     INT          NOT NULL DEFAULT 0,
    error_message   VARCHAR(500) NULL,
    started_at      DATETIME     NULL,
    finished_at     DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_record_stage (record_id, stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 化妆建议异步任务';