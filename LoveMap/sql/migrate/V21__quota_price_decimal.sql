-- V21：次卡支持小数单价，并将已有次卡订单按新价格回填。
-- AI 化妆建议 0.5 元/次；AI 约会建议、AI 心愿建议 0.3 元/次。

ALTER TABLE vip_order
    MODIFY COLUMN price_yuan DECIMAL(10, 2) NOT NULL COMMENT '下单时价格（元）';

UPDATE vip_order
SET price_yuan = ROUND(
        quota_count * CASE quota_type
            WHEN 1 THEN 0.5
            WHEN 2 THEN 0.3
            WHEN 3 THEN 0.3
            ELSE 0
        END,
        2
    )
WHERE order_type = 2
  AND quota_count IS NOT NULL
  AND quota_type IN (1, 2, 3);
