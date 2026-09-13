-- ============================================================
-- V14：通知支持类型与关联业务 ID
--
-- 背景：纪念日提醒产生的站内消息需要能定位到具体是哪条纪念日，
-- 前端 SystemMessageFloatBtn 才能显示「查看纪念日详情」并跳转
-- /profile?open=anniversary&id=<business_id>。
--
--   type        通知类型：1-系统通知，2-纪念日提醒，3-绑定相关，4-照片相关
--   business_id 关联业务 ID（纪念日提醒时为 anniversary.id），其他类型可空
--
-- 老数据：type 默认 1（系统通知），business_id 为空（前端不做跳转）。
-- ============================================================

ALTER TABLE notification
    ADD COLUMN type TINYINT NOT NULL DEFAULT 1 COMMENT '通知类型：1-系统通知，2-纪念日提醒，3-绑定相关，4-照片相关' AFTER text,
    ADD COLUMN business_id BIGINT NULL COMMENT '关联业务ID（纪念日提醒为纪念日ID）' AFTER type;
