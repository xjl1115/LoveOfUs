-- 心愿达成纪念照片：达成时上传 1 张照片，用于"记录卡片"展示与导出
ALTER TABLE wishlist_items
    ADD COLUMN achieved_photo_url VARCHAR(512) NULL COMMENT '达成纪念照片 URL' AFTER achieved_note;
