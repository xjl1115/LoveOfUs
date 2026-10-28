-- ============================================================
-- V23：AI 化妆建议次卡入账表 + 老订单补激活
--
-- 背景：MAKEOVER 次卡（quota_type=1）此前在顾问开通时
--       QuotaProductMapping.toQuotaType 返回 -1，被判为
--       "暂不支持自动开通"，导致用户购买后无法入账。
--       本次新建独立入账表，开通时按月入账，quota() 把当月
--       入账总和叠加到化妆建议月额度。quota_log 不参与。
--
--   grant_period    归属月份 YYYY-MM，下月不再计入 total
--   expires_at      本次入账过期时间 = 月底 23:59:59
--   order_no        来源订单号，唯一（防止重复开通幂等）
--
-- 与 quota_log 的边界：quota_log 仍负责 AI 约会/心愿两类配额流水；
-- 化妆建议走 makeover_record + 本表，不复用 quota_log，
-- 避免污染导出配额语义（quota_log.quota_type=1=导出）。
-- ============================================================

CREATE TABLE IF NOT EXISTS makeover_quota_grant (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL                COMMENT '用户ID',
    order_no      VARCHAR(32)  NOT NULL                COMMENT '来源订单号',
    grant_count   INT          NOT NULL                COMMENT '本次入账次数',
    grant_period  CHAR(7)      NOT NULL                COMMENT '归属月份 YYYY-MM',
    activated_at  DATETIME     NOT NULL                COMMENT '开通时间',
    expires_at    DATETIME     NOT NULL                COMMENT '过期时间（月底 23:59:59）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_period (user_id, grant_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 化妆建议次卡入账';

-- 老订单迁移：批量激活已卡在"待开通"的 MAKEOVER 次卡
-- 幂等：NOT EXISTS 兜底重复执行
INSERT INTO makeover_quota_grant (user_id, order_no, grant_count, grant_period, activated_at, expires_at)
SELECT o.user_id,
       o.order_no,
       o.quota_count,
       DATE_FORMAT(NOW(), '%Y-%m'),
       NOW(),
       DATE_SUB(DATE_ADD(DATE_FORMAT(NOW(), '%Y-%m-01'), INTERVAL 1 MONTH), INTERVAL 1 SECOND)
FROM vip_order o
WHERE o.status = 0
  AND o.order_type = 2
  AND o.quota_type = 1
  AND o.quota_count IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM makeover_quota_grant g WHERE g.order_no = o.order_no
  );

UPDATE vip_order
SET status = 1, activated_at = NOW()
WHERE status = 0
  AND order_type = 2
  AND quota_type = 1;
