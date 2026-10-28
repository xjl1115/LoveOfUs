-- V20：补齐次卡订单字段与配额流水表。
-- V17 已在部分环境执行，但旧版表结构没有次卡字段，导致下单时 SQL 异常。

ALTER TABLE vip_order
    ADD COLUMN order_type TINYINT NOT NULL DEFAULT 1 COMMENT '1=VIP卡，2=次卡' AFTER vip_level,
    ADD COLUMN quota_type TINYINT NULL COMMENT '1=AI化妆 2=AI约会 3=AI心愿（次卡使用）' AFTER order_type,
    ADD COLUMN quota_count INT NULL COMMENT '次卡购买次数' AFTER quota_type;

CREATE TABLE IF NOT EXISTS quota_log (
    id BIGINT AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    quota_type TINYINT NOT NULL COMMENT '1=导出，2=AI约会，3=AI心愿',
    used_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_user_type_time (user_id, quota_type, used_at),
    KEY idx_used_at (used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配额使用流水';
