-- ============================================================
-- V24：修复 vip_pricing 表中文乱码（双重编码）
--
-- 背景：V22 写入 vip_pricing 时，连入 MySQL 的连接字符集为 latin1，
--       导致 INSERT 里的中文字节（UTF-8 字节）被当成 latin1 字符
--       再按 utf8mb4 列编码存储。读出来时再用 UTF-8 解码就得到
--       "å‘¨å…" 这种二次编码字符串，顾问台表现为乱码。
--
-- 修复思路：把当前被错误存储的字符串按 latin1 还原成字节，再按
--       utf8mb4 解码。SQL：
--         CONVERT(CONVERT(CONVERT(col USING latin1) USING binary) USING utf8mb4)
--
-- 幂等性：双重编码的特征是出现 CP1252 特有的字符（U+2018 ‘ / U+2019 ’ /
--       U+201C “ / U+201D ” / U+2026 …）或 C1 控制字符（U+0080-U+009F），
--       正常 UTF-8 中文里这些不会出现。下面用 HEX 特征字节 C2 8X / C2 9X
--       作为守卫，只对受影响的行执行转换；如果数据已经正确，守卫
--       不命中，本脚本不会动它，可以安全重复执行。
-- ============================================================

UPDATE vip_pricing
SET product_name = CONVERT(CONVERT(CONVERT(product_name USING latin1) USING binary) USING utf8mb4),
    description  = CONVERT(CONVERT(CONVERT(description  USING latin1) USING binary) USING utf8mb4)
WHERE HEX(product_name) REGEXP 'C28[0-9A-F]|C29[0-9A-F]'
   OR HEX(description)  REGEXP 'C28[0-9A-F]|C29[0-9A-F]';
