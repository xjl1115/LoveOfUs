-- ============================================================
-- V30：收货地址 + 积分商品订单收货快照
--
-- 背景：
--   含金额的积分商品需要顾问线下收款后发货，因此下单前用户需先
--   选择收货地址，订单需快照记录收货信息（地址后续被修改/删除
--   不影响历史订单）。
--
-- 变更清单：
--   shipping_address      新建用户收货地址表（增删改查 + 默认地址）
--   point_exchange        + receiver_name / receiver_phone / shipping_address
-- ============================================================

SET NAMES utf8mb4;

-- ==================== 1. 用户收货地址 ====================
CREATE TABLE IF NOT EXISTS shipping_address (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址 ID',
    user_id         INT          NOT NULL                      COMMENT '用户 ID（与 point_exchange.user_id 对齐）',
    receiver_name   VARCHAR(50)  NOT NULL                      COMMENT '收货人姓名',
    receiver_phone  VARCHAR(20)  NOT NULL                      COMMENT '收货人手机号',
    province        VARCHAR(50)  NOT NULL                      COMMENT '省/直辖市',
    city            VARCHAR(50)  NOT NULL                      COMMENT '市/区',
    detail_address  VARCHAR(255) NOT NULL                      COMMENT '详细地址（街道门牌等）',
    is_default      TINYINT      NOT NULL DEFAULT 0            COMMENT '是否默认地址：0=否 1=是',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址';

-- ==================== 2. 订单收货快照 ====================
-- 下单时快照收货信息，地址后续变更不影响历史订单；历史行为 NULL。
ALTER TABLE point_exchange
    ADD COLUMN receiver_name    VARCHAR(50) NULL  COMMENT '收货人姓名（金额商品下单快照）',
    ADD COLUMN receiver_phone   VARCHAR(20) NULL  COMMENT '收货人手机号（金额商品下单快照）',
    ADD COLUMN shipping_address VARCHAR(255) NULL COMMENT '收货地址（省+市+详细，金额商品下单快照）';