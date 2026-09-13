-- 允许 group_id 为空：未绑定情侣（无 group）的用户也可使用心动模块（约会/心愿/必做）
-- 个人数据按创建人隔离：date_plans / wishlist_items 用 created_by，
-- things_completion 用 completed_by，things_url_rel 用 uploaded_by

ALTER TABLE date_plans
    MODIFY COLUMN group_id BIGINT NULL DEFAULT NULL COMMENT '所属情侣组 ID（未绑定情侣时为 NULL，按 created_by 隔离）';

ALTER TABLE wishlist_items
    MODIFY COLUMN group_id BIGINT NULL DEFAULT NULL COMMENT '所属情侣组 ID（未绑定情侣时为 NULL，按 created_by 隔离）';

ALTER TABLE things_completion
    MODIFY COLUMN group_id INT NULL DEFAULT NULL COMMENT '群组ID（情侣共享，未绑定时为 NULL，按 completed_by 隔离）';

ALTER TABLE things_url_rel
    MODIFY COLUMN group_id INT NULL DEFAULT NULL COMMENT '群组ID（情侣共享，未绑定时为 NULL，按 uploaded_by 隔离）';
