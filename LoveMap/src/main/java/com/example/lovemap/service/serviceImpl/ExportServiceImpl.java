package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.ExportConstant;
import com.example.lovemap.mapper.ExportMapper;
import com.example.lovemap.mapper.PhotoAlbumMapper;
import com.example.lovemap.mapper.PhotoMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.dto.ExportDTO;
import com.example.lovemap.model.entity.ExportRecord;
import com.example.lovemap.model.entity.Photo;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.ExportRecordVO;
import com.example.lovemap.service.ExportService;
import com.example.lovemap.service.SseService;
import com.example.lovemap.service.export.PdfExportService;
import com.example.lovemap.service.export.ZipExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导出服务实现
 * <p>
 * 职责：路由分发 + 任务管理 + 锁控制
 * 具体格式导出逻辑委托给 ZipExportService / PdfExportService。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ExportMapper exportMapper;
    private final PhotoMapper photoMapper;
    private final PhotoAlbumMapper photoAlbumMapper;
    private final UserMapper userMapper;
    private final ThreadPoolTaskExecutor exportTaskExecutor;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    // 具体格式导出服务
    private final ZipExportService zipExportService;
    private final PdfExportService pdfExportService;
    private final SseService sseService;

    @Value("${export.storage-path}")
    private String exportStoragePath;

    // 分布式锁（定义在 ExportConstant 中）

    // Lua 脚本（延迟加载）
    private DefaultRedisScript<Long> lockAcquireScript;
    private DefaultRedisScript<Long> lockReleaseScript;

    // 存储取消标记：taskId -> true
    private static final Set<Long> cancelFlags = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private DefaultRedisScript<Long> getLockAcquireScript() {
        if (lockAcquireScript == null) {
            lockAcquireScript = new DefaultRedisScript<>();
            lockAcquireScript.setLocation(new ClassPathResource("lua/lock_acquire.lua"));
            lockAcquireScript.setResultType(Long.class);
        }
        return lockAcquireScript;
    }

    private DefaultRedisScript<Long> getLockReleaseScript() {
        if (lockReleaseScript == null) {
            lockReleaseScript = new DefaultRedisScript<>();
            lockReleaseScript.setLocation(new ClassPathResource("lua/lock_release.lua"));
            lockReleaseScript.setResultType(Long.class);
        }
        return lockReleaseScript;
    }

    @Override
    @Transactional
    public Result<ExportRecordVO> createExport(Integer userId, ExportDTO dto) {
        log.info("[EXPORT] 进入 createExport userId={}, dto={}", userId, dto);
        // 1. 校验用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("[EXPORT] 用户不存在 userId={}", userId);
            return Result.notFound("用户不存在");
        }

        // 2. 校验格式
        String format = dto.getFormat();
        if (format == null || format.isEmpty()) {
            format = "zip";
        }
        if (!"zip".equals(format) && !"pdf".equals(format)) {
            log.warn("[EXPORT] 格式非法 userId={}, format={}", userId, format);
            return Result.badRequest("暂不支持的导出格式: " + format);
        }

        // 3. 收集照片ID
        List<Long> photoIds = resolvePhotoIds(userId, user.getGroupId(), dto);
        log.info("[EXPORT] resolvePhotoIds userId={}, groupId={}, exportType={}, resolvedCount={}",
                userId, user.getGroupId(), dto.getExportType(),
                photoIds == null ? 0 : photoIds.size());
        if (photoIds == null || photoIds.isEmpty()) {
            log.warn("[EXPORT] 解析照片ID为空 userId={}, exportType={}, startDate={}, endDate={}, albumId={}, selectedCount={}",
                    userId, dto.getExportType(), dto.getStartDate(), dto.getEndDate(),
                    dto.getAlbumId(),
                    dto.getPhotoIds() == null ? 0 : dto.getPhotoIds().size());
            return Result.badRequest("没有可导出的照片");
        }

        // 4. 查询照片信息，用于计数
        List<Photo> photos = photoMapper.selectBatchIds(photoIds);
        if (photos == null || photos.isEmpty()) {
            log.warn("[EXPORT] selectBatchIds 返回空 userId={}, idsCount={}", userId, photoIds.size());
            return Result.badRequest("没有可导出的照片");
        }

        // 5. 序列化选项
        String optionsJson;
        try {
            optionsJson = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("序列化导出选项失败", e);
            optionsJson = "{}";
        }

        // 6. 创建导出记录
        ExportRecord record = new ExportRecord();
        record.setUserId(userId.longValue());
        record.setFormat(format);
        record.setStartDate(dto.getStartDate());
        record.setEndDate(dto.getEndDate());
        record.setPhotoCount(photos.size());
        record.setStatus("pending");
        record.setOptions(optionsJson);
        record.setCreatedAt(LocalDateTime.now());

        exportMapper.insert(record);

        Long taskId = record.getId();
        log.info("[EXPORT] 创建导出任务成功 id={}, userId={}, format={}, photoCount={}, storagePath={}",
                taskId, userId, format, photos.size(), exportStoragePath);

        // 7. 尝试获取分布式锁，防止同一用户并发导出
        String lockKey = ExportConstant.EXPORT_LOCK_KEY_PREFIX + userId;
        Long locked = redisTemplate.execute(getLockAcquireScript(),
                Collections.singletonList(lockKey),
                String.valueOf(ExportConstant.EXPORT_LOCK_TIMEOUT));
        if (locked == null || locked == 0) {
            log.warn("[EXPORT] 用户 {} 已有导出任务正在执行，新任务 {} 排队等待 lockKey={}", userId, taskId, lockKey);
        } else {
            log.info("[EXPORT] 获取分布式锁成功 userId={}, taskId={}, lockKey={}", userId, taskId, lockKey);
        }

        // 8. 异步执行导出
        final String acquiredLockKey = lockKey;
        final boolean lockAcquired = (locked != null && locked == 1);
        try {
            exportTaskExecutor.execute(() -> processExport(taskId, userId, dto, photos, acquiredLockKey, lockAcquired));
            log.info("[EXPORT] 已提交异步任务 userId={}, taskId={}, executor={}", userId, taskId, exportTaskExecutor);
        } catch (Exception e) {
            log.error("[EXPORT] 提交异步任务失败 userId={}, taskId={}", userId, taskId, e);
            exportMapper.updateStatus(toStatusRecord(taskId, "failed"));
            return Result.error(ResultCode.INTERNAL_SERVER_ERROR, "提交异步任务失败：" + e.getMessage());
        }

        // 9. 返回VO
        return Result.success("导出任务已创建", toVO(record));
    }

    @Override
    public Result<ExportRecordVO> getExportStatus(Integer userId, Long id) {
        ExportRecord record = exportMapper.selectById(id);
        if (record == null) {
            return Result.notFound("导出任务不存在");
        }
        if (!record.getUserId().equals(userId.longValue())) {
            return Result.forbidden("无权访问该导出任务");
        }
        return Result.success(toVO(record));
    }

    @Override
    public Result<List<ExportRecordVO>> getExportHistory(Integer userId) {
        List<ExportRecord> records = exportMapper.selectByUserId(userId.longValue());
        List<ExportRecordVO> voList = records.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }

    @Override
    public ResponseEntity<Resource> downloadExport(Integer userId, Long id) {
        ExportRecord record = exportMapper.selectById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        if (!record.getUserId().equals(userId.longValue())) {
            return ResponseEntity.status(403).build();
        }
        if (!"completed".equals(record.getStatus())) {
            return ResponseEntity.badRequest().build();
        }

        String filePath = record.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        String filename;
        if ("pdf".equals(record.getFormat())) {
            filename = "loveofus_album_" + LocalDate.now() + ".pdf";
        } else {
            filename = "loveofus_photos_" + LocalDate.now() + ".zip";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(resource);
    }

    @Override
    public Result<Void> cancelExport(Integer userId, Long id) {
        ExportRecord record = exportMapper.selectById(id);
        if (record == null) {
            return Result.notFound("导出任务不存在");
        }
        if (!record.getUserId().equals(userId.longValue())) {
            return Result.forbidden("无权操作该导出任务");
        }
        // 只有处于 pending 或 processing 状态的任务才允许取消
        if (!"pending".equals(record.getStatus()) && !"processing".equals(record.getStatus())) {
            return Result.badRequest("当前任务状态无法取消");
        }

        // 标记取消
        cancelFlags.add(id);
        exportMapper.updateStatus(toStatusRecord(id, "failed"));

        // 清理已生成的文件
        if (record.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(record.getFilePath()));
            } catch (IOException e) {
                log.warn("取消导出时清理文件失败: {}", record.getFilePath());
            }
        }

        log.info("导出任务已取消: id={}", id);
        return Result.success("导出任务已取消", (Void) null);
    }

    // ==================== 异步任务处理 ====================

    /**
     * 异步处理导出任务
     */
    private void processExport(Long taskId, Integer userId, ExportDTO dto, List<Photo> photos,
                               String lockKey, boolean lockAcquired) {
        log.info("[EXPORT-ASYNC] 进入 processExport taskId={}, userId={}, format={}, photoCount={}",
                taskId, userId, dto.getFormat(), photos == null ? 0 : photos.size());
        try {
            // 检查是否被取消
            if (cancelFlags.contains(taskId)) {
                log.warn("[EXPORT-ASYNC] 任务已标记取消，退出 taskId={}", taskId);
                cancelFlags.remove(taskId);
                return;
            }

            // 如果未持有锁，尝试重试（最多 5 次，间隔 3 秒）
            if (!lockAcquired) {
                log.info("[EXPORT-ASYNC] 任务 {} 等待获取导出锁, userId={}", taskId, userId);
                for (int i = 0; i < 5; i++) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (cancelFlags.contains(taskId)) {
                        log.warn("[EXPORT-ASYNC] 等待锁期间任务被取消 taskId={}", taskId);
                        cancelFlags.remove(taskId);
                        return;
                    }
                    Long locked = redisTemplate.execute(getLockAcquireScript(),
                            Collections.singletonList(lockKey),
                            String.valueOf(ExportConstant.EXPORT_LOCK_TIMEOUT));
                    if (locked != null && locked == 1) {
                        lockAcquired = true;
                        log.info("[EXPORT-ASYNC] 任务 {} 第 {} 次重试获取导出锁成功", taskId, i + 1);
                        break;
                    }
                }
                if (!lockAcquired) {
                    log.warn("[EXPORT-ASYNC] 任务 {} 获取导出锁超时（5次重试结束），标记为失败", taskId);
                    exportMapper.updateStatus(toStatusRecord(taskId, "failed"));
                    return;
                }
            }

            // 更新状态为 processing
            log.info("[EXPORT-ASYNC] 更新状态为 processing taskId={}", taskId);
            exportMapper.updateStatus(toStatusRecord(taskId, "processing"));

            // 根据格式路由到具体的导出服务
            String outputPath;
            String format = dto.getFormat() != null ? dto.getFormat() : "zip";
            log.info("[EXPORT-ASYNC] 开始生成导出文件 taskId={}, format={}, storagePath={}",
                    taskId, format, exportStoragePath);
            outputPath = switch (format) {
                case "pdf" -> {
                    log.info("[EXPORT-ASYNC] 路由到 PdfExportService taskId={}", taskId);
                    yield pdfExportService.generatePdf(taskId, userId, photos, dto, exportStoragePath);
                }
                default -> {
                    log.info("[EXPORT-ASYNC] 路由到 ZipExportService taskId={}", taskId);
                    yield zipExportService.generateZip(taskId, userId, photos, dto, exportStoragePath);
                }
            };
            log.info("[EXPORT-ASYNC] 生成文件结束 taskId={}, outputPath={}", taskId, outputPath);

            // 检查是否被取消
            if (cancelFlags.contains(taskId)) {
                log.warn("[EXPORT-ASYNC] 生成期间任务被取消 taskId={}, outputPath={}", taskId, outputPath);
                cancelFlags.remove(taskId);
                try {
                    if (outputPath != null) Files.deleteIfExists(Paths.get(outputPath));
                } catch (IOException ignored) {
                }
                return;
            }

            // 获取文件大小
            File outputFile = new File(outputPath);
            if (!outputFile.exists()) {
                log.error("[EXPORT-ASYNC] 导出文件不存在 taskId={}, outputPath={}", taskId, outputPath);
                exportMapper.updateStatus(toStatusRecord(taskId, "failed"));
                return;
            }
            long fileSize = outputFile.length();
            log.info("[EXPORT-ASYNC] 导出文件大小 taskId={}, fileSize={} bytes", taskId, fileSize);

            // 更新为 completed
            ExportRecord updateRecord = new ExportRecord();
            updateRecord.setId(taskId);
            updateRecord.setFilePath(outputPath);
            updateRecord.setFileSize(fileSize);
            exportMapper.updateCompleted(updateRecord);

            log.info("[EXPORT-ASYNC] 导出任务完成 taskId={}, filePath={}, fileSize={} bytes", taskId, outputPath, fileSize);

            // 推送 SSE 通知到 AI 聊天界面
            try {
                java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("exportId", taskId);
                payload.put("format", dto.getFormat());
                payload.put("photoCount", photos == null ? 0 : photos.size());
                payload.put("fileSize", fileSize);
                payload.put("fileName", buildDownloadFileName(taskId, dto.getFormat()));
                payload.put("downloadUrl", "/api/exports/" + taskId + "/download");
                payload.put("completedAt", LocalDateTime.now().toString());
                sseService.sendEvent(userId, "ai-export-completed", payload);
                log.info("[EXPORT-ASYNC] SSE 已推送 ai-export-completed userId={}, taskId={}", userId, taskId);
            } catch (Exception sseEx) {
                log.warn("[EXPORT-ASYNC] SSE 推送失败 userId={}, taskId={}, err={}", userId, taskId, sseEx.getMessage());
            }

        } catch (Exception e) {
            log.error("[EXPORT-ASYNC] 导出任务失败 taskId={}, errMsg={}", taskId, e.getMessage(), e);
            try {
                exportMapper.updateStatus(toStatusRecord(taskId, "failed"));
                log.info("[EXPORT-ASYNC] 已将任务标记为 failed taskId={}", taskId);
            } catch (Exception ignored) {
            }
            // 失败时也推送 SSE，AI 聊天界面提示用户
            try {
                java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("exportId", taskId);
                payload.put("format", dto.getFormat());
                payload.put("status", "failed");
                payload.put("error", e.getMessage());
                sseService.sendEvent(userId, "ai-export-completed", payload);
            } catch (Exception sseEx) {
                log.warn("[EXPORT-ASYNC] 失败 SSE 推送异常 userId={}, taskId={}, err={}", userId, taskId, sseEx.getMessage());
            }
        } finally {
            cancelFlags.remove(taskId);
            // 释放分布式锁
            if (lockAcquired) {
                try {
                    redisTemplate.execute(getLockReleaseScript(), Collections.singletonList(lockKey));
                    log.info("[EXPORT-ASYNC] 已释放分布式锁 lockKey={}", lockKey);
                } catch (Exception e) {
                    log.error("[EXPORT-ASYNC] 释放导出锁失败 lockKey={}", lockKey, e);
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 解析需要导出的照片 ID 列表
     * <p>
     * 优先按 exportType 精确路由；当 exportType 为空但携带了日期范围时，
     * 自动按日期范围筛选，避免"前端只传日期却被当作全量导出"的漏筛选问题。
     */
    private List<Long> resolvePhotoIds(Integer userId, Long groupId, ExportDTO dto) {
        String exportType = dto.getExportType();

        if (exportType == null || exportType.isEmpty()) {
            // 未指定类型时：若有日期范围则按日期筛选，否则视为全部
            exportType = (dto.getStartDate() != null && dto.getEndDate() != null) ? "date" : "all";
        }

        switch (exportType) {
            case "selected":
                return dto.getPhotoIds() != null ? dto.getPhotoIds() : Collections.emptyList();
            case "album":
                if (dto.getAlbumId() == null) return Collections.emptyList();
                return photoAlbumMapper.selectPhotoIdsByAlbumId(dto.getAlbumId());
            case "date":
                if (dto.getStartDate() == null || dto.getEndDate() == null) return Collections.emptyList();
                if (groupId != null) {
                    return photoMapper.selectPhotoIdsByGroupIdAndDateRange(groupId, dto.getStartDate(), dto.getEndDate());
                } else {
                    return photoMapper.selectPhotoIdsByUserIdAndDateRange(userId.longValue(), dto.getStartDate(), dto.getEndDate());
                }
            default: // "all"
                if (groupId != null) {
                    return photoMapper.selectPhotoIdsByGroupId(groupId);
                } else {
                    return photoMapper.selectPhotoIdsByUserId(userId.longValue());
                }
        }
    }

    /**
     * 转换为 VO
     */
    private ExportRecordVO toVO(ExportRecord record) {
        ExportRecordVO vo = new ExportRecordVO();
        vo.setId(record.getId());
        vo.setStartDate(record.getStartDate() != null ? record.getStartDate().toString() : null);
        vo.setEndDate(record.getEndDate() != null ? record.getEndDate().toString() : null);
        vo.setPhotoCount(record.getPhotoCount());
        vo.setFormat(record.getFormat());
        vo.setStatus(record.getStatus());
        vo.setFilePath(record.getFilePath());
        vo.setFileSize(record.getFileSize());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setCompletedAt(record.getCompletedAt());
        return vo;
    }

    /**
     * 构造下载文件名（LoveOfUs-export-{taskId}.{ext}）
     */
    private String buildDownloadFileName(Long taskId, String format) {
        String ext = (format == null) ? "zip" : format.toLowerCase();
        return "LoveOfUs-export-" + taskId + "." + ext;
    }

    /**
     * 创建仅含 id 和 status 的临时记录，用于更新状态
     */
    private ExportRecord toStatusRecord(Long id, String status) {
        ExportRecord record = new ExportRecord();
        record.setId(id);
        record.setStatus(status);
        return record;
    }
}