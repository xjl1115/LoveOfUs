-- ============================================================
-- V2：聊天消息“按用户”软删除
-- 需求：任何登录用户都可以删除任意消息，删除后该消息只从删除者视图隐藏，对方仍可见。
-- 由于 is_deleted 是发送者级标记，不足以表达“接收者也删除”，因此新增按用户维度的删除记录表。
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_message_delete (
    id              BIGINT       NOT NULL AUTO_INCREMENT       COMMENT '主键',
    message_id      BIGINT       NOT NULL                      COMMENT '消息 ID',
    user_id         INT          NOT NULL                      COMMENT '删除者用户 ID',
    deleted_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '删除时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_user (message_id, user_id),
    KEY idx_user_msg (user_id, message_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='聊天消息按用户软删除记录';

-- 同步升级 chat_message.sql 的注释，保持文档一致
ALTER TABLE chat_message
    MODIFY COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '发送者是否删除（0=否 1=是；接收者删除请查 chat_message_delete 表）';