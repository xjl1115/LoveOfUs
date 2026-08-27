package com.example.lovemap.ai.service.serviceImpl;

import com.example.lovemap.ai.dto.AiRenameRequest;
import com.example.lovemap.ai.service.AiSessionService;
import com.example.lovemap.ai.service.AiShortTermMemoryService;
import com.example.lovemap.ai.vo.AiMessageVO;
import com.example.lovemap.ai.vo.AiSessionDetailVO;
import com.example.lovemap.ai.vo.AiSessionSummaryVO;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.mapper.AiChatMessageMapper;
import com.example.lovemap.mapper.AiChatSessionMapper;
import com.example.lovemap.model.entity.AiChatMessage;
import com.example.lovemap.model.entity.AiChatSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 会话 Service 实现
 * <p>
 * 缓存策略（Redis 作为缓存层）：
 * - ai:session:list:{userId}    → JSON 数组（会话列表）
 * - ai:session:detail:{userId}:{sessionId}  → JSON 对象（详情）
 * - TTL 10 分钟；变更写操作触发 invalidate
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSessionServiceImpl implements AiSessionService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiShortTermMemoryService shortTermMemory;

    /**
     * 自注入代理：用于在 deleteSession 内触发 @Async 方法，
     * 避免 self-invocation 绕过 Spring AOP 代理导致 @Async 失效。
     * @Lazy 防止构造期循环依赖。
     */
    @Autowired
    @Lazy
    private AiSessionServiceImpl self;

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String LIST_KEY_FMT = "ai:session:list:%d";
    private static final String DETAIL_KEY_FMT = "ai:session:detail:%d:%s";

    // ==================== 列表 ====================

    @Override
    public Result<List<AiSessionSummaryVO>> listSessions(Integer userId) {
        long uid = userId.longValue();
        String cacheKey = String.format(LIST_KEY_FMT, uid);

        // 1. 查 Redis
        String cached = safeGet(cacheKey);
        if (cached != null) {
            try {
                List<AiSessionSummaryVO> list = objectMapper.readValue(cached, new TypeReference<>() {});
                log.debug("[AI-SESSION] list hit cache userId={}, size={}", uid, list.size());
                return Result.success(list);
            } catch (Exception e) {
                log.warn("[AI-SESSION] list cache 反序列化失败", e);
            }
        }

        // 2. 查 DB
        List<AiSessionSummaryVO> list = sessionMapper.selectSummaryByUser(uid);
        if (list == null) list = List.of();

        // 3. 写 Redis
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(list), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[AI-SESSION] list 缓存写入失败", e);
        }
        return Result.success(list);
    }

    // ==================== 详情 ====================

    @Override
    public Result<AiSessionDetailVO> getSessionDetail(Integer userId, String sessionId) {
        long uid = userId.longValue();
        String cacheKey = String.format(DETAIL_KEY_FMT, uid, sessionId);

        String cached = safeGet(cacheKey);
        if (cached != null) {
            try {
                AiSessionDetailVO vo = objectMapper.readValue(cached, AiSessionDetailVO.class);
                return Result.success(vo);
            } catch (Exception e) {
                log.warn("[AI-SESSION] detail cache 反序列化失败", e);
            }
        }

        AiChatSession session = sessionMapper.selectBySessionId(sessionId, uid);
        if (session == null) {
            return Result.notFound("会话不存在或已删除");
        }
        List<AiMessageVO> messages = messageMapper.selectBySession(sessionId, uid);

        AiSessionDetailVO vo = new AiSessionDetailVO();
        vo.setSessionId(session.getSessionId());
        vo.setTitle(session.getTitle());
        vo.setPinned(session.getPinned());
        vo.setMessages(messages == null ? List.of() : messages);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(vo), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[AI-SESSION] detail 缓存写入失败", e);
        }
        return Result.success(vo);
    }

    // ==================== 创建（幂等） ====================

    @Override
    @Transactional
    public Result<AiSessionSummaryVO> createSession(Integer userId, String sessionId, String title) {
        long uid = userId.longValue();
        LocalDateTime now = LocalDateTime.now();
        AiChatSession session = sessionMapper.selectBySessionId(sessionId, uid);
        if (session != null) {
            return Result.success(toSummary(session));
        }

        AiChatSession newSession = new AiChatSession();
        newSession.setSessionId(sessionId);
        newSession.setUserId(uid);
        newSession.setTitle(title == null || title.isBlank() ? "新会话" : title);
        newSession.setPinned(0);
        newSession.setMessageCount(0);
        newSession.setLastActiveAt(now);
        newSession.setCreatedAt(now);
        newSession.setUpdatedAt(now);
        newSession.setIsDeleted(0);
        sessionMapper.insertIgnore(newSession);

        invalidateListCache((int) uid);
        AiChatSession created = sessionMapper.selectBySessionId(sessionId, uid);
        return Result.success(toSummary(created));
    }

    // ==================== 追加消息 ====================

    @Override
    @Transactional
    public Result<Void> appendMessage(Integer userId, String sessionId, AiMessageVO message) {
        long uid = userId.longValue();
        LocalDateTime now = LocalDateTime.now();

        // 自动创建会话（防御：前端可能没先调 create）
        AiChatSession session = sessionMapper.selectBySessionId(sessionId, uid);
        if (session == null) {
            AiChatSession ns = new AiChatSession();
            ns.setSessionId(sessionId);
            ns.setUserId(uid);
            ns.setTitle("新会话");
            ns.setPinned(0);
            ns.setMessageCount(0);
            ns.setLastActiveAt(now);
            ns.setCreatedAt(now);
            ns.setUpdatedAt(now);
            ns.setIsDeleted(0);
            sessionMapper.insertIgnore(ns);
        }

        // 写消息
        Integer nextSeq = messageMapper.selectMaxSeq(sessionId);
        if (nextSeq == null) nextSeq = -1;

        AiChatMessage msg = new AiChatMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(uid);
        msg.setMsgRole(message.getRole() == null ? "user" : message.getRole());
        msg.setMsgContent(message.getContent() == null ? "" : message.getContent());
        msg.setToolName(message.getToolName());
        msg.setSeq(nextSeq + 1);
        msg.setCreatedAt(message.getCreatedAt() == null ? now : message.getCreatedAt());
        messageMapper.insert(msg);

        // 触活会话
        sessionMapper.touch(sessionId, uid, now);

        // 失效缓存
        invalidateListCache((int) uid);
        invalidateDetailCache(uid, sessionId);
        return Result.success();
    }

    // ==================== 重命名 ====================

    @Override
    @Transactional
    public Result<AiSessionSummaryVO> renameSession(Integer userId, String sessionId, AiRenameRequest req) {
        long uid = userId.longValue();
        AiChatSession session = sessionMapper.selectBySessionId(sessionId, uid);
        if (session == null) return Result.notFound("会话不存在");

        LocalDateTime now = LocalDateTime.now();
        int rows = sessionMapper.updateTitle(sessionId, uid, req.getTitle().trim(), now);
        if (rows == 0) return Result.error(ResultCode.INTERNAL_SERVER_ERROR, "重命名失败");

        invalidateListCache((int) uid);
        invalidateDetailCache(uid, sessionId);
        AiChatSession updated = sessionMapper.selectBySessionId(sessionId, uid);
        return Result.success(toSummary(updated));
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public Result<Void> deleteSession(Integer userId, String sessionId) {
        long uid = userId.longValue();
        AiChatSession session = sessionMapper.selectBySessionId(sessionId, uid);
        if (session == null) return Result.success(); // 幂等

        // 同步阶段：仅软删除会话（让列表/详情查询立即看不到）
        // 物理删除消息 + 清理 Redis 短期记忆 → 异步执行，不阻塞响应
        sessionMapper.softDelete(sessionId, uid);
        invalidateListCache((int) uid);
        invalidateDetailCache(uid, sessionId);

        // 触发异步清理（消息物理删除 + Redis 短期记忆清理）
        // 通过自注入代理调用，避免 self-invocation 绕过 @Async
        self.cleanupSessionDataAsync(uid, sessionId);

        return Result.success();
    }

    /**
     * 异步清理：物理删除会话消息 + 清理 Redis 短期记忆
     * <p>
     * 为何可异步：
     * <ul>
     *   <li>软删除会话已在前置事务完成，列表/详情接口立即返回 404</li>
     *   <li>消息物理删除仅影响 MySQL 历史检索，前端列表/详情不可见</li>
     *   <li>Redis 短期记忆删除失败可接受（仅占 24h TTL 自然过期）</li>
     * </ul>
     * <p>
     * 失败处理：异常仅日志记录，不抛出（异步任务无法回滚 HTTP 响应）。
     * 极端情况下若彻底失败，靠 24h TTL 自然过期兜底。
     */
    @Async("aiSessionCleanupExecutor")
    public void cleanupSessionDataAsync(Long userId, String sessionId) {
        log.info("[AI-SESSION] 异步清理开始 userId={} sessionId={}", userId, sessionId);
        try {
            // 1. 物理删除消息
            int deleted = messageMapper.deleteBySession(sessionId, userId);
            log.info("[AI-SESSION] 异步清理：物理删除消息 userId={} sessionId={} rows={}",
                    userId, sessionId, deleted);
        } catch (Exception e) {
            log.error("[AI-SESSION] 异步清理消息失败 userId={} sessionId={}", userId, sessionId, e);
        }
        try {
            // 2. 清理 Redis 短期记忆
            shortTermMemory.clear(userId, sessionId);
            log.info("[AI-SESSION] 异步清理：Redis 短期记忆已清 userId={} sessionId={}",
                    userId, sessionId);
        } catch (Exception e) {
            log.error("[AI-SESSION] 异步清理 Redis 失败 userId={} sessionId={}", userId, sessionId, e);
        }
        log.info("[AI-SESSION] 异步清理完成 userId={} sessionId={}", userId, sessionId);
    }

    @Override
    public void invalidateListCache(Integer userId) {
        redisTemplate.delete(String.format(LIST_KEY_FMT, userId.longValue()));
    }

    // ==================== 工具 ====================

    private void invalidateDetailCache(long uid, String sessionId) {
        redisTemplate.delete(String.format(DETAIL_KEY_FMT, uid, sessionId));
    }

    private AiSessionSummaryVO toSummary(AiChatSession s) {
        if (s == null) return null;
        AiSessionSummaryVO v = new AiSessionSummaryVO();
        v.setSessionId(s.getSessionId());
        v.setTitle(s.getTitle());
        v.setMessageCount(s.getMessageCount());
        v.setPinned(s.getPinned());
        v.setLastActiveAt(s.getLastActiveAt());
        v.setCreatedAt(s.getCreatedAt());
        return v;
    }

    private String safeGet(String key) {
        try { return redisTemplate.opsForValue().get(key); }
        catch (Exception e) { log.warn("[AI-SESSION] redis get 失败 key={}", key, e); return null; }
    }
}