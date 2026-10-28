-- ============================================================
-- V25：补齐 vip_pricing 表次卡 quota_type 字段
--
-- 背景：V22__vip_pricing.sql 的 INSERT 语句漏写了 quota_type 字段，
--       导致三条次卡（quota_makeover / quota_date_plan / quota_wishlist）
--       的 quota_type 都是 NULL。
--
-- 影响：
--   1. 前端 /api/vip/pricing 返回的次卡项 quotaType 为 null，
--      被 VipDetail.vue 的过滤条件
--      `productType === 2 && quotaType != null` 全部过滤掉，
--      "购买次卡"按钮点击后报"次卡价格加载中，请稍后再试"。
--   2. 后端 createQuotaOrder 走 vipPricingService.getQuotaUnitPrice(quotaType)
--      按 quota_type 查单价，quota_type=NULL 时返回 null，
--      最终报"次卡类型不合法或未配置价格"。
--
-- 修复：用 product_code 唯一定位每条次卡，把 quota_type 补成
--       1=AI化妆 2=AI约会 3=AI心愿（与 VipConstant.QUOTA_PRODUCT_* 对齐）。
--
-- 幂等性：仅修正 quota_type IS NULL 的行；已经正确的不会被改写，
--       可以安全重复执行。
-- ============================================================

UPDATE vip_pricing SET quota_type = 1 WHERE product_code = 'quota_makeover'  AND quota_type IS NULL;
UPDATE vip_pricing SET quota_type = 2 WHERE product_code = 'quota_date_plan' AND quota_type IS NULL;
UPDATE vip_pricing SET quota_type = 3 WHERE product_code = 'quota_wishlist'  AND quota_type IS NULL;
