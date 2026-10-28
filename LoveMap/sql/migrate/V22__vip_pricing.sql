-- ============================================================
-- V22：VIP 与次卡价格表
--
-- 背景：周/月/季/年/永久卡与 AI 化妆/约会/心愿次卡的单价此前散落在
--       VipConstant 里，价格调整要改代码、重启服务。改为统一落库后，
--       顾问台可在线改价，前端开卡页拉取并缓存。
--
-- 表设计：
--   product_code  业务编码，唯一标识一个产品
--   product_type  1=VIP 卡 2=次卡（与 vip_order.order_type 对齐）
--   vip_level     VIP 档位（product_type=1 时非空）：1-周卡 2-月卡 3-季卡 4-年卡 5-永久
--   quota_type    次卡类型（product_type=2 时非空）：1=AI化妆 2=AI约会 3=AI心愿
--   price_yuan    单价（元），DECIMAL(10,2) 兼容次卡 0.3/0.5 元
--   sort_order    展示顺序（VIP 卡在前，次卡在后）
--
-- 老数据：表中种入与 VipConstant 当前取值一致的初始数据，重启后即生效。
--
-- 注意：本脚本在 INSERT 前显式 `SET NAMES utf8mb4`。
--       历史上有过教训：连接字符集走 latin1 时，"周卡" 的 UTF-8 字节
--       被当成 latin1 字符再按 utf8mb4 列存成二次编码（"å‘¨å…"），
--       顾问台表现为中文乱码。V24 用 CONVERT 把历史脏数据回正。
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS vip_pricing (
    id           BIGINT        NOT NULL AUTO_INCREMENT       COMMENT '主键',
    product_code VARCHAR(32)   NOT NULL                     COMMENT '业务编码：vip_week/vip_month/vip_quarter/vip_year/vip_forever/quota_makeover/quota_date_plan/quota_wishlist',
    product_name VARCHAR(64)   NOT NULL                     COMMENT '产品名称',
    product_type TINYINT       NOT NULL                     COMMENT '1=VIP 卡 2=次卡',
    vip_level    TINYINT       NULL                         COMMENT 'VIP 档位（product_type=1 时非空）：1-周卡 2-月卡 3-季卡 4-年卡 5-永久',
    quota_type   TINYINT       NULL                         COMMENT '次卡类型（product_type=2 时非空）：1=AI化妆 2=AI约会 3=AI心愿',
    price_yuan   DECIMAL(10, 2) NOT NULL                    COMMENT '单价（元）',
    description  VARCHAR(255)  NULL                         COMMENT '产品描述',
    sort_order   INT           NOT NULL DEFAULT 0           COMMENT '展示顺序：VIP 卡在前、次卡在后',
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (product_code),
    KEY idx_product_type (product_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP 与次卡价格表';

-- 初始数据与 VipConstant 现行价格一致
-- 注意：次卡行的 quota_type（1=AI化妆 2=AI约会 3=AI心愿）必须填，否则前端
-- /api/vip/pricing 接口会把次卡过滤掉，导致"购买次卡"按钮点击后报"价格加载中"。
INSERT INTO vip_pricing (product_code, product_name, product_type, vip_level, quota_type, price_yuan, description, sort_order) VALUES
('vip_week',        '周卡',       1, 1,    NULL,   6.00, 'VIP 周卡：7 天有效期',            10),
('vip_month',       '月卡',       1, 2,    NULL,  18.00, 'VIP 月卡：30 天有效期',           20),
('vip_quarter',     '季卡',       1, 3,    NULL,  48.00, 'VIP 季卡：90 天有效期',           30),
('vip_year',        '年卡',       1, 4,    NULL, 168.00, 'VIP 年卡：365 天有效期',          40),
('vip_forever',     '永久卡',     1, 5,    NULL, 398.00, 'VIP 永久卡：永久有效',            50),
('quota_makeover',  'AI 化妆建议', 2, NULL, 1,    0.50, 'AI 化妆建议次卡单价 0.5 元/次',   60),
('quota_date_plan', 'AI 约会建议', 2, NULL, 2,    0.30, 'AI 约会建议次卡单价 0.3 元/次',   70),
('quota_wishlist',  'AI 心愿建议', 2, NULL, 3,    0.30, 'AI 心愿建议次卡单价 0.3 元/次',   80);
