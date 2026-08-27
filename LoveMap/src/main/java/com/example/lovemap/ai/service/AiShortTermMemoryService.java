package com.example.lovemap.ai.service;

import com.example.lovemap.ai.vo.AiMessageVO;
import com.example.lovemap.mapper.AiChatMessageMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 短期记忆服务（Redis 存储）
 * <p>
 * 设计目标：
 * <ul>
 *   <li>同一会话内，AI 能记住最近 N 轮对话（默认 20 轮 ≈ 40 条消息）</li>
 *   <li>Redis Key TTL = 24h，超期自然失效</li>
 *   <li>Redis 失效时按需从 MySQL 重建（取该会话最近 N 条消息）</li>
 *   <li>长期记忆（MySQL 全量）由 AiSessionService 负责，本服务只读重建、不写</li>
 * </ul>
 * <p>
 * Redis 数据结构：
 * <pre>
 * key:   ai:memory:short:{userId}:{sessionId}
 * value: List&lt;String&gt;  每条 = JSON {role, content, ts}
 * </pre>
 * <p>
 * 为什么用 List 而不是 Hash：List 保留时序，AI 需要看到"按时间排列"的对话。
 *
 * <p><b>关于 Tool Call 消息：</b>
 * Tool 相关的中间消息（AiMessage.toolExecutionRequests + ToolExecutionResultMessage）属于 LLM 工具循环的内部状态，
 * 依赖于上一次 chat() 调用时的 toolSpecifications，跨调用无意义，<b>不应</b>写入短期记忆。
 * 只持久化 user / ai 这两种"自然语言"角色，足够续聊。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiShortTermMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiChatMessageMapper messageMapper;

    /** Redis Key 模板 */
    public static final String KEY_FMT = "ai:memory:short:%d:%s";

    /** 短期记忆最大消息数（20 轮 ≈ 40 条 user+ai）；超出 LTRIM 截断最早 */
    public static final int MAX_MESSAGES = 40;

    /** 短期记忆 TTL */
    public static final Duration TTL = Duration.ofHours(24);

    // ==================== 读：构建 LLM messages ====================

    /**
     * 加载会话的短期记忆（仅 user / ai 自然语言消息），用于拼装 LLM messages。
     * <p>
     * 流程：
     * <ol>
     *   <li>先尝试 Redis；若空，按需从 MySQL 重建</li>
     *   <li>转成 LangChain4j {@link ChatMessage} 列表</li>
     * </ol>
     *
     * @return 按时间正序的消息列表（不含本轮 user）；为空表示全新会话
     */
    public List<ChatMessage> loadMemory(Long userId, String sessionId) {
        List<MemoryEntry> entries = readEntries(userId, sessionId);
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessage> out = new ArrayList<>(entries.size());
        for (MemoryEntry e : entries) {
            ChatMessage m = toChatMessage(e);
            if (m != null) out.add(m);
        }
        return out;
    }

    // ==================== 写：追加新消息 ====================

    /**
     * 追加一条 user 消息到短期记忆
     */
    public void appendUserMessage(Long userId, String sessionId, String content) {
        append(userId, sessionId, "user", content);
    }

    /**
     * 追加一条 ai 消息到短期记忆
     */
    public void appendAiMessage(Long userId, String sessionId, String content) {
        append(userId, sessionId, "ai", content);
    }

    private void append(Long userId, String sessionId, String role, String content) {
        if (userId == null || sessionId == null || sessionId.isBlank()) return;
        if (content == null) content = "";
        String key = key(userId, sessionId);
        MemoryEntry entry = new MemoryEntry(role, content, System.currentTimeMillis());
        try {
            String json = objectMapper.writeValueAsString(entry);
            // 用 Pipeline：RPUSH + LTRIM + EXPIRE 三步原子写入
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                byte[] keyBytes = key.getBytes();
                connection.listCommands().rPush(keyBytes, json.getBytes());
                // 保留最后 MAX_MESSAGES 条（保留右侧 = 最新的）
                connection.listCommands().lTrim(keyBytes, -MAX_MESSAGES, -1);
                connection.keyCommands().expire(keyBytes, TTL.toSeconds());
                return null;
            });
        } catch (Exception e) {
            // 短期记忆写失败不应阻塞主流程；记录 warn
            log.warn("[AI-MEMORY] 写入失败 userId={} sessionId={} role={}", userId, sessionId, role, e);
        }
    }

    // ==================== 删：会话删除时清理 ====================

    /**
     * 删除某个会话的短期记忆
     */
    public void clear(Long userId, String sessionId) {
        if (userId == null || sessionId == null) return;
        try {
            redisTemplate.delete(key(userId, sessionId));
        } catch (Exception e) {
            log.warn("[AI-MEMORY] 清理失败 userId={} sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 删除某个用户的所有会话短期记忆（通过 SCAN 匹配 userId 前缀）
     */
    public void clearAllByUser(Long userId) {
        if (userId == null) return;
        String prefix = String.format(KEY_FMT, userId, "");
        try {
            java.util.Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("[AI-MEMORY] 清空用户全部短期记忆 userId={} count={}", userId, keys.size());
            }
        } catch (Exception e) {
            log.warn("[AI-MEMORY] 用户批量清理失败 userId={}", userId, e);
        }
    }

    // ==================== 内部 ====================

    /**
     * 读取会话的短期记忆条目；Redis 为空时按需从 MySQL 重建。
     */
    private List<MemoryEntry> readEntries(Long userId, String sessionId) {
        String key = key(userId, sessionId);
        List<String> raw;
        try {
            raw = redisTemplate.opsForList().range(key, 0, -1);
        } catch (Exception e) {
            log.warn("[AI-MEMORY] 读取失败，尝试 MySQL 重建 userId={} sessionId={}", userId, sessionId, e);
            raw = null;
        }
        if (raw == null || raw.isEmpty()) {
            return rebuildFromMysql(userId, sessionId);
        }
        List<MemoryEntry> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            try {
                MemoryEntry e = objectMapper.readValue(s, MemoryEntry.class);
                out.add(e);
            } catch (Exception parseEx) {
                log.warn("[AI-MEMORY] 单条解析失败，跳过: {}", s, parseEx);
            }
        }
        return out;
    }

    /**
     * 从 MySQL 拉取该会话最近 N 条 user/ai 消息，重建到 Redis。
     */
    private List<MemoryEntry> rebuildFromMysql(Long userId, String sessionId) {
        try {
            List<AiMessageVO> recent = messageMapper.selectBySession(sessionId, userId);
            if (recent == null || recent.isEmpty()) {
                return Collections.emptyList();
            }
            // MySQL 默认按 seq ASC；保留最后 MAX_MESSAGES 条
            int from = Math.max(0, recent.size() - MAX_MESSAGES);
            List<AiMessageVO> tail = recent.subList(from, recent.size());

            // 写入 Redis
            String key = key(userId, sessionId);
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                // DEL 再 RPUSH，避免残留
                connection.keyCommands().del(key.getBytes());
                for (AiMessageVO vo : tail) {
                    String role = vo.getRole() == null ? "user" : vo.getRole();
                    // 只重建 user/ai 自然语言角色；tool 角色跳过
                    if (!"user".equals(role) && !"ai".equals(role)) continue;
                    if (vo.getContent() == null) continue;
                    MemoryEntry entry = new MemoryEntry(
                            role,
                            vo.getContent(),
                            vo.getCreatedAt() == null ? System.currentTimeMillis()
                                    : vo.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    );
                    try {
                        connection.listCommands().rPush(key.getBytes(),
                                objectMapper.writeValueAsString(entry).getBytes());
                    } catch (Exception jsonEx) {
                        log.warn("[AI-MEMORY] 重建序列化失败", jsonEx);
                    }
                }
                connection.keyCommands().expire(key.getBytes(), TTL.toSeconds());
                return null;
            });
            log.info("[AI-MEMORY] 从 MySQL 重建短期记忆 userId={} sessionId={} count={}",
                    userId, sessionId, tail.size());

            // 直接返回内存中的结果，避免再次读 Redis
            List<MemoryEntry> out = new ArrayList<>(tail.size());
            for (AiMessageVO vo : tail) {
                String role = vo.getRole() == null ? "user" : vo.getRole();
                if (!"user".equals(role) && !"ai".equals(role)) continue;
                if (vo.getContent() == null) continue;
                out.add(new MemoryEntry(
                        role,
                        vo.getContent(),
                        vo.getCreatedAt() == null ? System.currentTimeMillis()
                                : vo.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                ));
            }
            return out;
        } catch (Exception e) {
            log.warn("[AI-MEMORY] MySQL 重建失败 userId={} sessionId={}", userId, sessionId, e);
            return Collections.emptyList();
        }
    }

    private ChatMessage toChatMessage(MemoryEntry e) {
        if (e == null || e.role == null) return null;
        return switch (e.role) {
            case "user" -> UserMessage.from(e.content == null ? "" : e.content);
            case "ai" -> AiMessage.from(e.content == null ? "" : e.content);
            default -> null;
        };
    }

    private String key(Long userId, String sessionId) {
        return String.format(KEY_FMT, userId, sessionId);
    }

    /**
     * 短期记忆条目（user/ai 自然语言）
     */
    public static final class MemoryEntry {
        public String role;
        public String content;
        public long ts;

        public MemoryEntry() {}

        public MemoryEntry(String role, String content, long ts) {
            this.role = role;
            this.content = content;
            this.ts = ts;
        }
    }
}
