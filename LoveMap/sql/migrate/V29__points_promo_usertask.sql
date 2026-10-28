-- ============================================================
-- V29：积分商城（金额/促销）+ 情侣任务（自动识别 / 按周期存储）
--
-- 背景：V28 上线后按业务反馈做三处增强：
--   1. 积分商品支持「积分 + 金额」双价，并支持促销价；
--      有金额的商品走「下单 → 顾问线下收款 → 后台确认发放」流程，
--      与 VIP 卡开通流程一致（不再纯积分即时兑换）。
--   2. 情侣任务由「用户手动点完成」改为「系统自动识别」，
--      完成方式 complete_type 决定由哪个系统事件触发。
--   3. 任务状态从「按情侣组共享的完成记录」改为「按用户 × 任务 × 周期」的
--      三态状态机（0 未完成 / 1 已完成待领取 / 2 已领取），
--      零点懒加载：查询时按需补默认值，不写空行，避免数据膨胀。
--
-- 变更清单：
--   point_product        + price_amount / promo_status / promo_points / promo_amount
--   point_exchange       + order_no / price_amount / confirmed_at（status 语义扩展）
--   couple_task          + complete_type / period_type / claim_required
--   user_task            新建（用户任务状态）
--   couple_task_completion  删除（已被 user_task 取代，原表 0 行无数据迁移）
-- ============================================================

SET NAMES utf8mb4;

-- ==================== 1. 积分商品：金额与促销 ====================
ALTER TABLE point_product
    ADD COLUMN price_amount DECIMAL(10,2) NULL COMMENT '需付款金额（元），NULL=仅积分兑换',
    ADD COLUMN promo_status TINYINT NOT NULL DEFAULT 0 COMMENT '促销：0=关 1=开',
    ADD COLUMN promo_points INT NULL COMMENT '促销积分',
    ADD COLUMN promo_amount DECIMAL(10,2) NULL COMMENT '促销金额（元）';

-- ==================== 2. 兑换记录：升级为「订单」 ====================
-- status 语义扩展：0=待顾问确认收款 1=已发放 2=已取消
-- 历史行 status=1 表示“已成功兑换”，与新语义兼容，无需数据订正。
ALTER TABLE point_exchange
    ADD COLUMN order_no VARCHAR(32) NULL COMMENT '订单号（金额商品下单生成）',
    ADD COLUMN price_amount DECIMAL(10,2) NULL COMMENT '需付款金额（元），NULL=纯积分兑换',
    ADD COLUMN confirmed_at DATETIME NULL COMMENT '顾问确认收款时间';

ALTER TABLE point_exchange
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '0=待确认 1=已发放 2=已取消';

ALTER TABLE point_exchange
    ADD UNIQUE KEY uk_order_no (order_no);

-- ==================== 3. 情侣任务定义：完成方式与周期 ====================
ALTER TABLE couple_task
    ADD COLUMN complete_type VARCHAR(32) NOT NULL DEFAULT 'manual'
        COMMENT '完成方式：manual/message_send/photo_upload/date_plan_create',
    ADD COLUMN period_type VARCHAR(16) NOT NULL DEFAULT 'daily'
        COMMENT '周期：daily/weekly/monthly',
    ADD COLUMN claim_required TINYINT NOT NULL DEFAULT 1 COMMENT '1=需手动领取积分';

-- ==================== 4. 用户任务状态表 ====================
-- 用户 × 任务 × 周期 一行；查询时按需 LEFT JOIN 补默认 0，仅在完成时才落行，
-- 避免「日活 × 任务数」的空行膨胀。
-- 周/月任务复用同表：period_key 存「周期起始日」（周一 / 每月 1 号）。
CREATE TABLE IF NOT EXISTS user_task (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        INT         NOT NULL COMMENT '用户 ID',
    task_id        BIGINT      NOT NULL COMMENT '任务 ID',
    period_type    VARCHAR(16) NOT NULL COMMENT 'daily/weekly/monthly',
    period_key     CHAR(10)    NOT NULL COMMENT '周期键：日=2026-10-17，周=周一日期，月=2026-10-01',
    status         TINYINT     NOT NULL DEFAULT 0 COMMENT '0=未完成 1=已完成待领取 2=已领取',
    progress       INT         NOT NULL DEFAULT 0 COMMENT '进度',
    completed_at   DATETIME    NULL COMMENT '完成时间',
    claimed_at     DATETIME    NULL COMMENT '领取积分时间',
    points_awarded INT         NOT NULL DEFAULT 0 COMMENT '本次发放积分',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_task_period (user_id, task_id, period_key),
    KEY idx_user_period (user_id, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户任务状态';

-- ==================== 5. 种子任务：绑定可自动识别的系统事件 ====================
-- 需求：任务不再由用户手动点完成，改为系统自动识别。
-- V28 的 8 条种子任务按 sort_order 10~80 排列（10=写心里话、20=拍合照、
-- 30=视频通话、40=为对方做一件小事、50=一起听歌、60=小确幸、70=晚安、80=规划约会），
-- 其中 30/40/50 三条没有可识别的数据源，按方案改为系统能识别的行为（改文案 + 绑定事件）。
-- 用 sort_order 定位而非 content，避免中文匹配受编码影响；顾问自建的任务保持不变。

-- 5.1 有数据源的任务：直接绑定事件
UPDATE couple_task SET complete_type = 'message_send'
 WHERE sort_order IN (10, 70) AND status = 1;

UPDATE couple_task SET complete_type = 'photo_upload'
 WHERE sort_order IN (20, 60) AND status = 1;

UPDATE couple_task SET complete_type = 'date_plan_create'
 WHERE sort_order = 80 AND status = 1;

-- 5.2 无数据源的 3 条：改文案 + 绑定事件
-- 原「视频通话 10 分钟」→ 发一条消息即可被系统识别
UPDATE couple_task
   SET content = '给 TA 发一条暖心消息',
       description = '主动给 TA 发一条消息，聊聊今天发生的事。',
       complete_type = 'message_send'
 WHERE sort_order = 30 AND status = 1;

-- 原「为对方做一件小事」→ 新建约会计划即可被系统识别
UPDATE couple_task
   SET content = '为 TA 新建一个约会计划',
       description = '在「心动 Hub」里为 TA 新建一个约会计划。',
       complete_type = 'date_plan_create'
 WHERE sort_order = 40 AND status = 1;

-- 原「一起听一首歌」→ 分享今天的心情（消息）即可被系统识别
UPDATE couple_task
   SET content = '给 TA 分享今天的心情',
       description = '把今天的心情或一段心里话发给 TA。',
       complete_type = 'message_send'
 WHERE sort_order = 50 AND status = 1;

-- ==================== 6. 删除旧表 ====================
-- 上一轮建立的 couple_task_completion（按情侣组共享）已被 user_task 取代；
-- 该表当前为 0 行，无需数据迁移。
DROP TABLE IF EXISTS couple_task_completion;
