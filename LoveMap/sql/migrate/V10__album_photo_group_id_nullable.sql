-- 允许 group_id 为空：未绑定情侣（无 group）的用户也可创建相册、上传照片
-- 个人数据通过 user_id 隔离，查询侧已有 group_id IS NULL AND user_id = ? 的兜底逻辑

ALTER TABLE album
    MODIFY COLUMN group_id BIGINT NULL DEFAULT NULL COMMENT '情侣id';

-- photo 主键原为 (id, group_id)，group_id 允许 NULL 后无法作为主键列，改为仅 id
ALTER TABLE photo
    DROP PRIMARY KEY,
    MODIFY COLUMN group_id INT NULL DEFAULT NULL COMMENT '情侣id',
    ADD PRIMARY KEY (id);
