package com.example.lovemap.mapper;

import com.example.lovemap.ai.vo.AiMessageVO;
import com.example.lovemap.model.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiChatMessageMapper {

    /** 追加一条消息（返回自增 id） */
    int insert(AiChatMessage msg);

    /** 按会话查询全部消息（按 seq ASC） */
    List<AiMessageVO> selectBySession(@Param("sessionId") String sessionId,
                                       @Param("userId") Long userId);

    /** 物理删除会话下的所有消息 */
    int deleteBySession(@Param("sessionId") String sessionId,
                         @Param("userId") Long userId);

    /** 查询会话最大 seq（无则返回 -1） */
    Integer selectMaxSeq(@Param("sessionId") String sessionId);
}