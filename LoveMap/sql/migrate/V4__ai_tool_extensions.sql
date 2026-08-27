-- P0/P1 AI 工具扩展：新增 mood_log 与 anniversary_reminder 表

CREATE TABLE IF NOT EXISTS mood_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id     BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    mood         VARCHAR(20) NOT NULL COMMENT '心情 emoji/名称：happy/sad/excited/tired/love/angry',
    mood_score   TINYINT    NOT NULL COMMENT '心情强度 1-5',
    note         VARCHAR(255) DEFAULT NULL COMMENT '一句话备注',
    log_date     DATE       NOT NULL COMMENT '打卡日期（精确到天）',
    created_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user_day (group_id, user_id, log_date),
    INDEX idx_group_day (group_id, log_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日心情打卡';

CREATE TABLE IF NOT EXISTS anniversary_reminder (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id     BIGINT NOT NULL,
    anniversary_id BIGINT NOT NULL,
    remind_days  INT NOT NULL COMMENT '提前几天提醒（与 anniversary.remind_days 一致）',
    remind_date  DATE NOT NULL COMMENT '提醒日期',
    is_sent      TINYINT(1) NOT NULL DEFAULT 0,
    sent_at      DATETIME DEFAULT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_anniversary_days (anniversary_id, remind_days),
    INDEX idx_group_date (group_id, remind_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='纪念日提醒计划';