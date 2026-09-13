-- ============================================================
-- V17：VIP 订单表
--
-- 背景：下单只登记订单，不直接开通 VIP。
--       用户下单 → 专属顾问收款 → 顾问调开通接口完成开通。
--
--   status        0-待开通（已下单，等顾问收款后开通），1-已开通，2-已取消
--   order_no      订单号，顾问据此定位订单
--   group_id      下单时的情侣组 ID，用于开通后清除双方 UserVO 缓存
--   created_at    下单时间
--   activated_at  顾问开通时间，VIP 到期时间以此为基准
--
-- 一人同时只保留一张待开通订单：再次下单会把旧的待开通订单置为已取消。
-- ============================================================

CREATE TABLE IF NOT EXISTS vip_order (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no     VARCHAR(32)  NOT NULL                COMMENT '订单号',
    user_id      BIGINT       NOT NULL                COMMENT '下单用户 ID',
    group_id     BIGINT       NULL                    COMMENT '下单时的情侣组 ID',
    vip_level    TINYINT      NOT NULL                COMMENT 'VIP 档位：1-周卡，2-月卡，3-季卡，4-年卡，5-永久',
    price_yuan   INT          NOT NULL                COMMENT '下单时价格（元）',
    status       TINYINT      NOT NULL DEFAULT 0      COMMENT '0=待开通 1=已开通 2=已取消',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    activated_at DATETIME     NULL                    COMMENT '顾问开通时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_status (user_id, status),
    KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP 订单';
