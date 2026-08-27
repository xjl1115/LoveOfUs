package com.example.lovemap.controller;

import com.example.lovemap.chat.ChatSessionRegistry;
import com.example.lovemap.common.PageResult;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.ChatPresenceDTO;
import com.example.lovemap.model.vo.ChatMessageVO;
import com.example.lovemap.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @PostMapping("/in-chat/enter")
    @Operation(summary = "进入聊天页", description = "标记当前用户停留在此伴侣的聊天页，用于实时已读")
    public Result<Void> enterChat(@RequestAttribute("userId") Integer userId,
                                  @RequestBody ChatPresenceDTO dto) {
        return chatService.enterChat(userId, dto);
    }

    @PostMapping("/in-chat/heartbeat")
    @Operation(summary = "聊天页心跳", description = "刷新停留状态心跳，防止超过 TTL 被清理")
    public Result<Void> heartbeat(@RequestAttribute("userId") Integer userId,
                                  @RequestBody(required = false) ChatPresenceDTO dto) {
        return chatService.heartbeat(userId, dto);
    }

    @PostMapping("/in-chat/leave")
    @Operation(summary = "离开聊天页", description = "清除停留状态标记")
    public Result<Void> leaveChat(@RequestAttribute("userId") Integer userId,
                                  @RequestBody(required = false) ChatPresenceDTO dto) {
        return chatService.leaveChat(userId, dto);
    }

    @GetMapping("/online-status")
    @Operation(summary = "查询伴侣在线状态", description = "伴侣是否在线 + 当前聊天总在线人数")
    public Result<OnlineStatusVO> onlineStatus(@RequestAttribute("userId") Integer userId) {
        OnlineStatusVO vo = new OnlineStatusVO();
        vo.setOnlineCount(chatSessionRegistry.onlineCount());
        return Result.success(vo);
    }

    @PostMapping("/message/{id}")
    @Operation(summary = "软删除单条消息", description = "仅能删除本人发送的消息（仅本人视图隐藏）")
    public Result<Boolean> deleteMessage(@RequestAttribute("userId") Integer userId,
                                          @PathVariable("id") Long id) {
        return chatService.deleteMessage(userId, id);
    }

    @PostMapping("/messages/delete-batch")
    @Operation(summary = "批量软删除", description = "批量软删除本人发送的消息")
    public Result<Integer> deleteMessages(@RequestAttribute("userId") Integer userId,
                                          @RequestBody List<Long> ids) {
        return chatService.deleteMessages(userId, ids);
    }

    @PostMapping("/message/{id}/recall")
    @Operation(summary = "撤回消息", description = "仅本人发送的消息且 2 分钟内有效")
    public Result<Boolean> recallMessage(@RequestAttribute("userId") Integer userId,
                                         @PathVariable("id") Long id) {
        return chatService.recallMessage(userId, id);
    }

    @PostMapping("/clear-local")
    @Operation(summary = "清空本地聊天记录", description = "仅清空本人视图，伴侣仍可看到所有消息")
    public Result<Integer> clearLocalHistory(@RequestAttribute("userId") Integer userId) {
        return chatService.clearLocalHistory(userId);
    }

    @Setter
    @Getter
    public static class OnlineStatusVO {
        private int onlineCount;

    }
}