-- ============================================================
-- V28：每日签到 / 积分商城 / 情侣任务
--
-- 背景：提升日常粘性、增加产品丰富度。
--   1. 每日签到获得「心动积分」，连续签到有里程碑加成（3/7/10/20/30 天）；
--   2. 积分可在「积分商城」兑换实物/虚拟商品；
--   3. 情侣任务由顾问控制台配置，完成后获得积分。
--
-- 新增表：
--   user_points               用户积分账户（每用户一行）
--   user_points_log           积分流水（每次变动留痕，方便客服查询与审计）
--   point_product             积分商品（顾问控制台 CRUD，前端只读 + 缓存）
--   point_exchange            兑换记录（流水）
--   couple_task               情侣任务定义（顾问控制台 CRUD，前端只读 + 缓存）
--   couple_task_completion    任务完成记录（按情侣组共享，幂等）
--
-- 关键设计：
--   - 积分账户用 UPDATE 原子增减（points = points + #{delta}），避免 ABA；
--   - 兑换扣减库存：UPDATE ... SET stock = stock - 1 WHERE id = ? AND stock > 0
--     配合受影响行数判断；
--   - 任务完成按 (group_id, task_id, DATE(completed_at)) 唯一，幂等；
--   - 商品/任务列表按 VIP 定价同款方式缓存到 Redis (point:product:list / couple:task:list)，
--     写操作主动失效缓存。
-- ============================================================

SET NAMES utf8mb4;

-- ==================== 1. 用户积分账户 ====================
CREATE TABLE IF NOT EXISTS user_points (
    user_id              INT          NOT NULL                     COMMENT '用户 ID（与 user.id 对齐）',
    points               INT          NOT NULL DEFAULT 0           COMMENT '当前可用积分',
    total_points         INT          NOT NULL DEFAULT 0           COMMENT '历史累计获得',
    continuous_days      INT          NOT NULL DEFAULT 0           COMMENT '当前连续签到天数（断签清零）',
    total_check_in_days  INT          NOT NULL DEFAULT 0           COMMENT '累计签到天数',
    last_check_in_at     DATE         NULL                          COMMENT '上次签到日期',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分账户';

-- ==================== 2. 积分流水 ====================
CREATE TABLE IF NOT EXISTS user_points_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '流水 ID',
    user_id         INT          NOT NULL                          COMMENT '用户 ID',
    change_type     TINYINT      NOT NULL                          COMMENT '变动类型：1=签到 2=完成任务 3=兑换商品 4=管理员调整',
    change_points   INT          NOT NULL                          COMMENT '变动积分（正=获得，负=消耗）',
    balance_after   INT          NOT NULL                          COMMENT '变动后余额',
    ref_type        VARCHAR(32)  NULL                              COMMENT '关联类型：check_in/task/exchange/admin',
    ref_id          BIGINT       NULL                              COMMENT '关联 ID',
    remark          VARCHAR(255) NULL                              COMMENT '备注',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at),
    KEY idx_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

-- ==================== 3. 积分商品 ====================
CREATE TABLE IF NOT EXISTS point_product (
    id              BIGINT        NOT NULL AUTO_INCREMENT           COMMENT '商品 ID',
    name            VARCHAR(64)   NOT NULL                          COMMENT '商品名称',
    description     VARCHAR(500)  NULL                              COMMENT '商品描述',
    points_cost     INT           NOT NULL                          COMMENT '兑换所需积分',
    stock           INT           NOT NULL DEFAULT 0                COMMENT '库存，-1 表示不限',
    image_url       VARCHAR(500)  NULL                              COMMENT '商品图片 URL',
    status          TINYINT       NOT NULL DEFAULT 1                COMMENT '0=下架 1=上架',
    sort_order      INT           NOT NULL DEFAULT 0                COMMENT '展示顺序',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_sort (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商品';

-- ==================== 4. 兑换记录 ====================
CREATE TABLE IF NOT EXISTS point_exchange (
    id              BIGINT       NOT NULL AUTO_INCREMENT            COMMENT '兑换记录 ID',
    user_id         INT          NOT NULL                           COMMENT '兑换用户',
    product_id      BIGINT       NOT NULL                           COMMENT '商品 ID',
    product_name    VARCHAR(64)  NOT NULL                           COMMENT '冗余商品名称',
    points_cost     INT          NOT NULL                           COMMENT '单价积分',
    quantity        INT          NOT NULL DEFAULT 1                 COMMENT '兑换数量',
    total_points    INT          NOT NULL                           COMMENT '消耗总积分 = points_cost * quantity',
    status          TINYINT      NOT NULL DEFAULT 1                  COMMENT '1=成功',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at),
    KEY idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换记录';

-- ==================== 5. 情侣任务定义 ====================
CREATE TABLE IF NOT EXISTS couple_task (
    id              BIGINT        NOT NULL AUTO_INCREMENT           COMMENT '任务 ID',
    content         VARCHAR(255)  NOT NULL                          COMMENT '任务内容',
    description     VARCHAR(500)  NULL                              COMMENT '任务描述',
    icon            VARCHAR(32)   NULL                              COMMENT 'emoji 小图标',
    points          INT           NOT NULL DEFAULT 0                COMMENT '完成后获得积分',
    status          TINYINT       NOT NULL DEFAULT 1                COMMENT '0=禁用 1=启用',
    sort_order      INT           NOT NULL DEFAULT 0                COMMENT '展示顺序',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_sort (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情侣任务定义';

-- ==================== 6. 任务完成记录 ====================
CREATE TABLE IF NOT EXISTS couple_task_completion (
    id              BIGINT       NOT NULL AUTO_INCREMENT            COMMENT '完成记录 ID',
    task_id         BIGINT       NOT NULL                           COMMENT '任务 ID',
    user_id         INT          NOT NULL                           COMMENT '完成用户',
    group_id        BIGINT       NULL                               COMMENT '情侣组 ID',
    points_awarded  INT          NOT NULL                           COMMENT '本次获得的积分',
    note            VARCHAR(255) NULL                               COMMENT '完成备注',
    completed_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_task (user_id, task_id),
    KEY idx_group (group_id, completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情侣任务完成记录';

-- ==================== 7. 初始数据：示例任务 ====================
INSERT INTO couple_task (content, description, icon, points, status, sort_order) VALUES
('给 TA 写一段心里话',  '把此刻的感受写下来发给对方，可以是感谢、思念或承诺。', '💌', 20,  1, 10),
('一起拍一张合照',      '今天拍一张两个人的合照，上传到相册。',              '📸', 15,  1, 20),
('视频通话 10 分钟',    '今天主动发起一次视频通话，聊聊近况。',              '📞', 10,  1, 30),
('为对方做一件小事',    '比如买早餐、按摩、泡一杯茶，记录在备注里。',         '☕', 25,  1, 40),
('一起听一首歌',        '分享一首当下心情契合的歌曲给对方。',                '🎵', 10,  1, 50),
('记录今天的小确幸',    '在相册里加一张今天的照片或心情日记。',              '🌷', 15,  1, 60),
('睡前互道晚安',        '今晚睡前互相说一句晚安，可以是文字或语音。',        '🌙', 10,  1, 70),
('一起规划下次约会',    '在「心动 Hub」里新建一个约会计划。',              '💖', 30,  1, 80);
