package com.example.lovemap.mapper;

import com.example.lovemap.ai.vo.AiSessionSummaryVO;
import com.example.lovemap.model.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiChatSessionMapper {

    /** 插入会话（使用 INSERT IGNORE 幂等） */
    int insertIgnore(AiChatSession session);

    /** 按业务 sessionId 查询 */
    AiChatSession selectBySessionId(@Param("sessionId") String sessionId,
                                     @Param("userId") Long userId);

    /** 当前用户的全部会话（按 last_active_at DESC） */
    List<AiChatSummaryRow> selectListByUser(@Param("userId") Long userId);

    /** 更新会话标题 */
    int updateTitle(@Param("sessionId") String sessionId,
                     @Param("userId") Long userId,
                     @Param("title") String title,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /** 更新最后活跃时间和消息数（增量 +1） */
    int touch(@Param("sessionId") String sessionId,
               @Param("userId") Long userId,
               @Param("lastActiveAt") LocalDateTime lastActiveAt);

    /** 更新消息数 */
    int updateMessageCount(@Param("sessionId") String sessionId,
                            @Param("userId") Long userId,
                            @Param("delta") int delta);

    /** 软删除会话 */
    int softDelete(@Param("sessionId") String sessionId,
                    @Param("userId") Long userId);

    /** 软删除会话并级联删除消息 */
    int softDeleteCascade(@Param("sessionId") String sessionId,
                           @Param("userId") Long userId);

    /** 行结果：会话列表（投影 VO） */
    @org.apache.ibatis.annotations.ResultType(AiSessionSummaryVO.class)
    List<AiSessionSummaryVO> selectSummaryByUser(@Param("userId") Long userId);

    /** Mapper 行类型：用于上面 selectSummaryByUser 的 MyBatis 映射 */
    class AiChatSummaryRow {}
}