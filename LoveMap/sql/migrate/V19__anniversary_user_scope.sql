-- ============================================================
-- V19：纪念日支持未绑定情侣的个人使用
--
-- 背景：anniversary.group_id 此前 NOT NULL，未绑定情侣（user.group_id 为 NULL）
--       的用户访问纪念日模块会被直接拒绝（"请先绑定情侣关系"），整个模块不可用。
--
-- 改法：与 date_plans / wishlist_items 保持同一套归属写法——
--       绑定时按 group_id 共享，未绑定时 group_id 为 NULL、按 user_id 归属个人。
--       归属判定统一为：
--           (group_id = #{groupId} OR (group_id IS NULL AND user_id = #{userId}))
--
-- 同时放开 anniversary_reminder.group_id：个人纪念日没有 group，提醒记录写 NULL。
--
-- 老数据：现存记录的 group_id 保持不变，user_id 为 NULL，归属逻辑不受影响。
-- ============================================================

ALTER TABLE anniversary
    MODIFY COLUMN group_id INT NULL DEFAULT NULL COMMENT '关联group表；未绑定情侣时为 NULL，按 user_id 隔离',
    ADD COLUMN user_id BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID；仅未绑定情侣的个人纪念日使用' AFTER group_id,
    ADD KEY idx_user_id (user_id);

ALTER TABLE anniversary_reminder
    MODIFY COLUMN group_id BIGINT NULL DEFAULT NULL COMMENT '所属情侣组ID；个人纪念日为 NULL';
