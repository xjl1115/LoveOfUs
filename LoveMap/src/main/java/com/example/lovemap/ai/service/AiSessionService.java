package com.example.lovemap.ai.service;

import com.example.lovemap.ai.dto.AiRenameRequest;
import com.example.lovemap.ai.vo.AiMessageVO;
import com.example.lovemap.ai.vo.AiSessionDetailVO;
import com.example.lovemap.ai.vo.AiSessionSummaryVO;
import com.example.lovemap.common.Result;

import java.util.List;

/**
 * AI 会话 Service 接口
 */
public interface AiSessionService {

    /**
     * 获取当前用户的所有会话（列表页）
     */
    Result<List<AiSessionSummaryVO>> listSessions(Integer userId);

    /**
     * 获取会话详情（含消息列表）
     */
    Result<AiSessionDetailVO> getSessionDetail(Integer userId, String sessionId);

    /**
     * 创建新会话（幂等：若 sessionId 已存在则返回旧记录）
     */
    Result<AiSessionSummaryVO> createSession(Integer userId, String sessionId, String title);

    /**
     * 追加一条消息（同时更新会话 messageCount + lastActiveAt）
     */
    Result<Void> appendMessage(Integer userId, String sessionId, AiMessageVO message);

    /**
     * 重命名会话
     */
    Result<AiSessionSummaryVO> renameSession(Integer userId, String sessionId, AiRenameRequest req);

    /**
     * 删除会话（级联删除消息，DB 软删 + Redis 清除缓存）
     */
    Result<Void> deleteSession(Integer userId, String sessionId);

    /**
     * 使当前用户的全部会话缓存失效
     */
    void invalidateListCache(Integer userId);
}