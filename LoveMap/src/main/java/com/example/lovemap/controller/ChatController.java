package com.example.lovemap.controller;

import com.example.lovemap.chat.ChatSessionRegistry;
import com.example.lovemap.common.PageResult;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.ChatMessageVO;
import com.example.lovemap.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天 REST 控制器（仅历史消息/未读数/标记已读，实时收发走 WebSocket）
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Tag(name = "聊天模块", description = "伴侣 1 对 1 聊天接口")
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionRegistry chatSessionRegistry;

    @GetMapping("/history")
    @Operation(summary = "聊天历史消息", description = "分页查询与伴侣的历史聊天消息（按时间正序）")
    public Result<PageResult<ChatMessageVO>> history(@RequestAttribute("userId") Integer userId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return chatService.getHistory(userId, page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读消息数", description = "伴侣发来的未读消息数量")
    public Result<Long> unreadCount(@RequestAttribute("userId") Integer userId) {
        return chatService.getUnreadCount(userId);
    }

    @PostMapping("/read-all")
    @Operation(summary = "标记全部已读", description = "将伴侣发来的全部未读消息标记为已读")
    public Result<Integer> markAllRead(@RequestAttribute("userId") Integer userId) {
        return chatService.markAllRead(userId);
    }

    @GetMapping("/online-status")
    @Operation(summary = "查询伴侣在线状态", description = "伴侣是否在线 + 当前聊天总在线人数")
    public Result<OnlineStatusVO> onlineStatus(@RequestAttribute("userId") Integer userId) {
        OnlineStatusVO vo = new OnlineStatusVO();
        vo.setOnlineCount(chatSessionRegistry.onlineCount());
        return Result.success(vo);
    }

    public static class OnlineStatusVO {
        private int onlineCount;
        public int getOnlineCount() { return onlineCount; }
        public void setOnlineCount(int onlineCount) { this.onlineCount = onlineCount; }
    }
}
