-- date_plans.budget 的语义是预算等级（free/low/mid/high/luxury），不是金额。
-- 原 DECIMAL(10,2) 会让写入 `mid` 这类等级值报错：
--   Incorrect decimal value: 'mid' for column 'budget' at row 1

ALTER TABLE date_plans
    MODIFY COLUMN budget VARCHAR(20) DEFAULT NULL COMMENT '预算等级：free/low/mid/high/luxury';
