-- ============================================================
-- V27：次卡入账改为"不过期，用完为止"
--
-- 背景：次卡入账此前按周期叠加进额度（AI 每周 / 化妆建议每月），
--       未用完的次数会随周期重置而失效。现改为：入账次数永久有效，
--       先扣本周期基础额度，基础额度用尽后才消耗已购次数，直到用完为止。
--
-- 做法：给"消耗记录"打来源标记，把两类消耗分开统计：
--   quota_source = 1  周期基础额度（AI 每周 / 导出每月 / 化妆建议每月，周期重置）
--   quota_source = 2  已购次卡（不过期，用完为止）
--
-- 历史数据默认按 1 归类：改动前购买的次卡实际未生效（入账被算成已用），
-- 历史消耗都来自周期基础额度，归类为 1 与事实一致。
--
-- 一次性执行（MySQL 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报
-- Duplicate column name，属预期）。
-- ============================================================

ALTER TABLE quota_log
    ADD COLUMN quota_source TINYINT NOT NULL DEFAULT 1
        COMMENT '消耗来源：1=周期基础额度，2=次卡入账（不过期）' AFTER quota_type;

ALTER TABLE makeover_record
    ADD COLUMN quota_source TINYINT NOT NULL DEFAULT 1
        COMMENT '消耗来源：1=月度基础额度，2=次卡入账（不过期）' AFTER scene_text;
