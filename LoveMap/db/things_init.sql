-- 情侣必做 100 件事 - 新增表 + things.icon
-- 适用于已有 things / things_url 表的环境

-- ============================================================
-- 已有表 things：新增 icon 字段（emoji 文本，列表左侧展示）
-- ============================================================
ALTER TABLE things ADD COLUMN icon VARCHAR(16) DEFAULT NULL COMMENT '事项左侧图标（emoji 文本）' AFTER description;

-- ============================================================
-- 表 1：things_completion（完成记录，情侣共享）
-- ============================================================
CREATE TABLE IF NOT EXISTS things_completion (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    thing_id     INT          NOT NULL COMMENT '事项 ID（关联 things.id）',
    group_id     INT          NULL DEFAULT NULL COMMENT '群组ID（情侣共享，未绑定时为 NULL，按 completed_by 隔离）',
    completed_by INT          NOT NULL COMMENT '完成人用户ID',
    completed_at DATETIME     NOT NULL COMMENT '完成时间',
    note         VARCHAR(500) DEFAULT NULL COMMENT '完成笔记',
    INDEX idx_thing_group (thing_id, group_id),
    INDEX idx_group (group_id),
    UNIQUE KEY uk_thing_group (thing_id, group_id) COMMENT '同一 couple 对同一事项只能有一条记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情侣必做 100 件事完成记录';

-- ============================================================
-- 表 2：things_url_rel（事项-图片关联表）
-- ============================================================
CREATE TABLE IF NOT EXISTS things_url_rel (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    thing_id     INT      NOT NULL COMMENT '事项 ID（关联 things.id）',
    url_id       INT      NOT NULL COMMENT '图片 URL ID（关联 things_url.id）',
    group_id     INT      NULL DEFAULT NULL COMMENT '群组ID（情侣共享，未绑定时为 NULL，按 uploaded_by 隔离）',
    uploaded_by  INT      NOT NULL COMMENT '上传人用户ID',
    created_at   DATETIME NOT NULL COMMENT '上传时间',
    INDEX idx_thing_group (thing_id, group_id),
    INDEX idx_url (url_id),
    INDEX idx_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情侣必做事项-图片关联表';

-- ============================================================
-- 已有表加建议索引（性能优化，可选）
-- ============================================================
-- things 主键已为 id，无需索引

-- ============================================================
-- 说明
-- ============================================================
-- 启动时 ThingsSeedRunner 会检测 things 表为空时自动插入 100 项种子数据。
-- 字段 completed 保留但不作为完成依据（保留字段，未来如需全表扫描快速判定再用）。
-- things_completion 表的唯一约束 uk_thing_group 防止同一情侣对同一事项重复完成。
