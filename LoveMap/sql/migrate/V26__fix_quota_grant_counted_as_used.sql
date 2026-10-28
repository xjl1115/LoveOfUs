-- ============================================================
-- V26：修正次卡入账被当成"已使用"的历史流水
--
-- 背景：次卡开通原实现把入账按次数写进 quota_log（N 条），
--       而剩余次数 = 本周期额度 - COUNT(quota_log 本周期行数)，
--       于是入账反而被算成已用，次数不增反减（表现为"次数没有更新/仍是 0"）。
--       现改为：入账凭证 = vip_order（order_type=2 且 status=1），
--       配额上限按本周期已开通次卡的 quota_count 求和，不再写 quota_log。
--
-- 本脚本只做数据修正：删掉历史上被误写成"使用流水"的入账记录。
--
-- 识别方式：与已开通次卡订单同一用户、同一秒（quota_log.used_at = vip_order.activated_at）、
--           同一 quota_type（2=AI约会 3=AI心愿）的流水。
--           quota_type=1（AI化妆）走 makeover_quota_grant，本就不写 quota_log，
--           quota_type=1（导出与备份）无次卡产品，均不受影响。
-- 幂等：流水删除后再次执行不会命中。
-- 注意：若某笔真实使用恰好与次卡开通在同一秒，会被一并删除（概率极低，
--       仅影响历史数据；修复后不再产生此类误写流水）。
-- ============================================================

DELETE q
FROM quota_log q
         JOIN vip_order o
              ON o.user_id = q.user_id
                  AND o.activated_at = q.used_at
                  AND o.order_type = 2
                  AND o.status = 1
                  AND o.quota_type IN (2, 3)
                  AND o.quota_type = q.quota_type;
