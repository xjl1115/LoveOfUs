-- ============================================================
-- V16：用户 VIP 等级与到期时间
--
-- 背景：新增 VIP 服务，用户购买周/月/季/年/永久卡后升级为 VIP。
--
--   vip_level      VIP 等级：0-普通用户，1-周卡，2-月卡，3-季卡，4-年卡，5-永久
--   vip_expire_at  到期时间；永久卡为 NULL，未开通也为 NULL
--
-- 归属：VIP 归属情侣组（groupId），付款方写入自己的 user 行，
--       读取时取组内成员中等级最高（同等级取到期更晚）的一方生效。
--
-- 老数据：vip_level 默认 0（普通用户），vip_expire_at 为 NULL。
-- ============================================================

ALTER TABLE user
    ADD COLUMN vip_level TINYINT NOT NULL DEFAULT 0 COMMENT 'VIP等级：0-普通用户，1-周卡，2-月卡，3-季卡，4-年卡，5-永久' AFTER gender,
    ADD COLUMN vip_expire_at DATETIME NULL COMMENT 'VIP到期时间，NULL表示未开通或永久有效' AFTER vip_level;
