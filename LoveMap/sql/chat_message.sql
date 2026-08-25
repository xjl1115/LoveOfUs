-- ============================================================
-- 聊天消息表（夫妻/伴侣 1 对 1 聊天）
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT       COMMENT '消息ID',
    sender_id       INT          NOT NULL                      COMMENT '发送者用户ID',
    receiver_id     INT          NOT NULL                      COMMENT '接收者用户ID（绑定伴侣）',
    content         VARCHAR(2000) NOT NULL                     COMMENT '消息内容（纯文本 1-2000 字符）',
    msg_type        TINYINT      NOT NULL DEFAULT 1            COMMENT '消息类型：1=文本',
    is_read         TINYINT(1)   NOT NULL DEFAULT 0            COMMENT '接收者是否已读（0=未读 1=已读）',
    read_at         DATETIME     DEFAULT NULL                  COMMENT '接收者已读时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0            COMMENT '发送者是否删除（0=否 1=是，软删除，仅本人视图隐藏；接收者删除请查 chat_message_delete 表）',
    revoked         TINYINT(1)   NOT NULL DEFAULT 0            COMMENT '是否撤回（0=否 1=是，双方视图都显示撤回态）',
    revoked_at      DATETIME     DEFAULT NULL                  COMMENT '撤回时间',
    PRIMARY KEY (id),
    KEY idx_receiver_unread (receiver_id, is_read, created_at),
    KEY idx_sender_receiver (sender_id, receiver_id, created_at),
    KEY idx_pair (sender_id, receiver_id, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='聊天消息表（伴侣 1 对 1）';
