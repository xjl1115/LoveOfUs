-- AI 化妆建议记录：新增改造总结字段
-- 与 face_features / suggestions 同期由 AI 一次返回，整体总结 + 化妆步骤详解
ALTER TABLE makeover_record
    ADD COLUMN summary JSON NULL COMMENT '改造总结（{overall:string, steps:string[]}）' AFTER suggestions;
