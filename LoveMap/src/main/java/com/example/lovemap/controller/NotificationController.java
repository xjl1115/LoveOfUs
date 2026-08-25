package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.NotificationListVO;
import com.example.lovemap.service.NotificationService;
import com.example.lovemap.service.SseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息通知控制器
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "消息通知模块", description = "消息通知相关接口")
public class NotificationController {

    private final SseService sseService;
    private final NotificationService notificationService;

    /**
     * SSE 订阅消息推送
     * 建立 Server-Sent Events 连接，实时接收服务端推送的消息通知
     */
    @GetMapping("/subscribe")
    @Operation(summary = "SSE订阅消息推送", description = "建立SSE连接，实时接收消息通知、未读数更新等事件")
    public SseEmitter subscribe(@RequestAttribute("userId") Integer userId) {
        log.info("用户建立SSE连接, userId: {}", userId);

        // 创建 SSE 连接
        SseEmitter emitter = sseService.createConnection(userId);

        // 发送连接成功事件
        sseService.sendEvent(userId, "connected", Map.of(
                "userId", userId,
                "connectedAt", LocalDateTime.now()
        ));

        return emitter;
    }

    /**
     * 查询消息通知列表
     */
    @GetMapping
    @Operation(summary = "查询消息通知列表", description = "分页查询用户的通知列表，支持筛选已读/未读")
    public Result<NotificationListVO> getNotificationList(
            @RequestAttribute("userId") Integer userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Integer isRead) {
        return notificationService.getNotificationList(userId, page, size, isRead);
    }

    /**
     * 查询未读消息数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "查询未读消息数量", description = "获取用户的未读通知数量")
    public Result<Long> getUnreadCount(@RequestAttribute("userId") Integer userId) {
        return notificationService.getUnreadCount(userId);
    }

    /**
     * 标记消息为已读
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "标记消息为已读", description = "将指定通知标记为已读状态")
    public Result<Void> markAsRead(@RequestAttribute("userId") Integer userId, @PathVariable Integer id) {
        return notificationService.markAsRead(userId, id);
    }

    /**
     * 标记所有消息为已读
     */
    @PutMapping("/read-all")
    @Operation(summary = "标记所有消息为已读", description = "将用户的所有未读通知标记为已读")
    public Result<Integer> markAllAsRead(@RequestAttribute("userId") Integer userId) {
        return notificationService.markAllAsRead(userId);
    }

    /**
     * 删除消息通知
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除消息通知", description = "删除指定的通知记录")
    public Result<Void> deleteNotification(@RequestAttribute("userId") Integer userId, @PathVariable Integer id) {
        return notificationService.deleteNotification(userId, id);
    }

    /**
     * 清空所有已读消息
     */
    @DeleteMapping("/clear")
    @Operation(summary = "清空所有已读消息", description = "删除用户的所有已读通知")
    public Result<Integer> clearReadNotifications(@RequestAttribute("userId") Integer userId) {
        return notificationService.clearReadNotifications(userId);
    }

    /**
     * 获取当前在线连接数
     */
    @GetMapping("/online-count")
    @Operation(summary = "获取在线连接数")
    public Result<Integer> getOnlineCount() {
        return Result.success(sseService.getOnlineCount());
    }
}
