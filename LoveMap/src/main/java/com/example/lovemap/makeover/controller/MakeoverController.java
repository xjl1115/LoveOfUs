package com.example.lovemap.makeover.controller;

import com.example.lovemap.common.PageResult;
import com.example.lovemap.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.lovemap.makeover.constant.MakeoverConstant;
import com.example.lovemap.makeover.mapper.MakeoverRecordMapper;
import com.example.lovemap.makeover.service.MakeoverShareService;
import com.example.lovemap.makeover.task.MakeoverSseSubscriber;
import com.example.lovemap.model.dto.MakeoverCreateDTO;
import com.example.lovemap.model.entity.MakeoverRecord;
import com.example.lovemap.model.vo.MakeoverCreateVO;
import com.example.lovemap.model.vo.MakeoverDetailVO;
import com.example.lovemap.model.vo.MakeoverListVO;
import com.example.lovemap.model.vo.MakeoverQuotaVO;
import com.example.lovemap.service.MakeoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 化妆建议 Controller
 */
@Slf4j
@RestController
@RequestMapping("/makeover")
@RequiredArgsConstructor
@Tag(name = "AI 化妆建议")
public class MakeoverController {

    private final MakeoverService makeoverService;
    private final MakeoverRecordMapper recordMapper;
    private final MakeoverSseSubscriber sseSubscriber;
    private final MakeoverShareService shareService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "提交化妆建议（异步）")
    public Result<MakeoverCreateVO> create(
            @RequestAttribute("userId") Integer userId,
            @RequestPart("image") MultipartFile image,
            @RequestParam("scene") String scene,
            @RequestParam(value = "description", required = false) String description) {
        MakeoverCreateDTO dto = new MakeoverCreateDTO();
        dto.setScene(scene);
        dto.setDescription(description);
        return Result.success(makeoverService.create(userId, image, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "化妆建议详情")
    public Result<MakeoverDetailVO> detail(
            @RequestAttribute("userId") Integer userId,
            @PathVariable Long id) {
        return Result.success(makeoverService.detail(userId, id));
    }

    @GetMapping
    @Operation(summary = "我的化妆建议列表")
    public Result<PageResult<MakeoverListVO>> list(
            @RequestAttribute("userId") Integer userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(makeoverService.list(userId, page, size));
    }

    @GetMapping("/quota")
    @Operation(summary = "本月化妆建议额度（剩余/总数）")
    public Result<MakeoverQuotaVO> quota(@RequestAttribute("userId") Integer userId) {
        return Result.success(makeoverService.quota(userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除化妆建议")
    public Result<Void> delete(
            @RequestAttribute("userId") Integer userId,
            @PathVariable Long id) {
        makeoverService.softDelete(userId, id);
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消化妆建议")
    public Result<Void> cancel(
            @RequestAttribute("userId") Integer userId,
            @PathVariable Long id) {
        makeoverService.cancel(userId, id);
        return Result.success();
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "分享妆造建议给伴侣（生成卡片聊天消息）")
    public Result<Long> share(
            @RequestAttribute("userId") Integer userId,
            @PathVariable Long id) {
        Long chatMessageId = shareService.share(userId, id);
        return Result.success(chatMessageId);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅化妆建议进度（SSE）")
    public SseEmitter stream(
            @RequestAttribute("userId") Integer userId,
            @PathVariable Long id) {

        // 鉴权：仅记录所有者可订阅
        MakeoverRecord record = recordMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new IllegalArgumentException("记录不存在");
        }

        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        sseSubscriber.register(id, emitter);

        // 立即推送当前快照，避免前端空白期
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("stage", stageOf(record.getStatus()));
            snapshot.put("status", statusOf(record.getStatus()));
            emitter.send(SseEmitter.event().name("stage").data(objectMapper.writeValueAsString(snapshot)));

            if (record.getStatus() != null
                    && (record.getStatus() == MakeoverConstant.STATUS_DONE
                    || record.getStatus() == MakeoverConstant.STATUS_FAILED
                    || record.getStatus() == MakeoverConstant.STATUS_CANCELED)) {
                emitter.complete();
            }
        } catch (IOException e) {
            log.debug("[Makeover-SSE] 快照推送失败 recordId={}", id);
        }

        return emitter;
    }

    private String stageOf(Byte status) {
        if (status == null) return "pending";
        return switch (status) {
            case 1 -> "analyze";
            case 2 -> "image_edit";
            case 3 -> "done";
            case 4 -> "failed";
            case 5 -> "canceled";
            default -> "pending";
        };
    }

    private String statusOf(Byte status) {
        if (status == null) return "pending";
        return switch (status) {
            case 3 -> "done";
            case 4 -> "failed";
            case 5 -> "canceled";
            default -> "running";
        };
    }
}
