-- 约会照片：完成约会时可选上传，落库为 URL JSON 数组（文件本体走 OSS/本地存储）
ALTER TABLE date_plans
    ADD COLUMN photos TEXT NULL COMMENT '约会照片 URL，JSON 数组' AFTER tips;