-- ============================================================
-- V18：情侣必做 100 件事新增 category（分类）字段
--
-- 背景：此前数据库、后端 VO、接口都没有分类字段，前端只能靠标题关键词猜分类，
--       结果 100 条里 76 条不属于任何分类（只在"全部"里可见），六个分项页签
--       命中数之和只有 25，选分项看不到完整列表。
--
-- 改法：分类落库成数据，前端直接按 category 字段过滤，分项之和严格等于 100。
--
--   category  分类：travel 旅行 / romance 浪漫 / daily 日常
--                  food 美食 / memory 纪念 / growth 成长
--
-- 回填依据：01_lovemap.sql（部署用的全量 dump）与 db/things_init.sql 里那套 100 条清单，
--           逐条人工归类，按 id 分组回填。
--
-- 老数据：category 默认为 NULL，由下面的语句统一回填。
-- ============================================================

ALTER TABLE things
    ADD COLUMN category VARCHAR(16) DEFAULT NULL
        COMMENT '分类：travel旅行/romance浪漫/daily日常/food美食/memory纪念/growth成长' AFTER icon;

-- 旅行（17）
UPDATE things SET category = 'travel'
WHERE id IN (20, 21, 22, 26, 27, 29, 34, 37, 38, 40, 56, 61, 64, 70, 74, 90, 97);

-- 浪漫（14）
UPDATE things SET category = 'romance'
WHERE id IN (18, 39, 51, 52, 62, 66, 69, 72, 76, 77, 81, 86, 89, 100);

-- 日常（41）
UPDATE things SET category = 'daily'
WHERE id IN (1, 2, 3, 4, 5, 6, 7, 10, 12, 13, 14, 15, 16, 17, 19, 23, 24, 25, 28, 30,
             32, 41, 43, 44, 45, 46, 47, 48, 58, 60, 63, 65, 71, 75, 78, 79, 80, 82, 83, 84, 91);

-- 美食（11）
UPDATE things SET category = 'food'
WHERE id IN (8, 9, 11, 31, 50, 55, 59, 67, 88, 93, 94);

-- 纪念（9）
UPDATE things SET category = 'memory'
WHERE id IN (35, 42, 53, 54, 68, 92, 95, 98, 99);

-- 成长（8）
UPDATE things SET category = 'growth'
WHERE id IN (33, 36, 49, 57, 73, 85, 87, 96);
