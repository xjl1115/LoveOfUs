-- ============================================================
-- V3: AI 聊天会话表
-- 存储用户的 AI 对话会话列表与详情（消息快照）
-- 关联 user_id；与 chat_message 表无关（这是 AI 助手，不是情侣聊天）
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id      VARCHAR(64)     NOT NULL                COMMENT '业务会话ID（UUID，前端生成）',
    user_id         BIGINT          NOT NULL                COMMENT '所属用户ID',
    title           VARCHAR(100)    NOT NULL DEFAULT '新会话' COMMENT '会话标题（自动生成或用户手动重命名）',
    pinned          TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '是否置顶：0-否，1-是',
    message_count   INT             NOT NULL DEFAULT 0      COMMENT '消息总数（缓存字段，避免每次 count）',
    last_active_at  DATETIME        NOT NULL                COMMENT '最后活跃时间（用于排序）',
    created_at      DATETIME        NOT NULL                COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL                COMMENT '更新时间',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '软删除：0-否，1-是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_user_active (user_id, last_active_at, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天会话';

-- 会话消息详情表
-- 一条消息对应一行；按 session_id 分组
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id      VARCHAR(64)     NOT NULL                COMMENT '会话ID',
    user_id         BIGINT          NOT NULL                COMMENT '所属用户ID',
    msg_role        VARCHAR(16)     NOT NULL                COMMENT 'user/ai/tool/system',
    msg_content     MEDIUMTEXT      NOT NULL                COMMENT '消息内容',
    tool_name       VARCHAR(64)     DEFAULT NULL            COMMENT '工具名称（role=tool 时有值）',
    seq             INT             NOT NULL                COMMENT '消息在会话内的序号',
    created_at      DATETIME        NOT NULL                COMMENT '消息创建时间',
    PRIMARY KEY (id),
    KEY idx_session_seq (session_id, seq),
    KEY idx_user_session (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天会话消息详情';