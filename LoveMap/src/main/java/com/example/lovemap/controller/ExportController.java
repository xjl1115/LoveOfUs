package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.ExportDTO;
import com.example.lovemap.model.vo.ExportRecordVO;
import com.example.lovemap.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 导出 Controller
 */
@Slf4j
@RestController
@RequestMapping("/exports")
@RequiredArgsConstructor
@Tag(name = "导出管理")
public class ExportController {

    private final ExportService exportService;

    @PostMapping
    @Operation(summary = "创建导出任务")
    public Result<ExportRecordVO> createExport(
            @RequestAttribute("userId") Integer userId,
            @Valid @RequestBody ExportDTO dto) {
        return exportService.createExport(userId, dto);
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "获取导出任务状态")
    public Result<ExportRecordVO> getExportStatus(
            @RequestAttribute("userId") Integer userId,
            @PathVariable("id") Long id) {
        return exportService.getExportStatus(userId, id);
    }

    @GetMapping
    @Operation(summary = "获取导出历史列表")
    public Result<List<ExportRecordVO>> getExportHistory(
            @RequestAttribute("userId") Integer userId) {
        return exportService.getExportHistory(userId);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "下载导出文件")
    public ResponseEntity<Resource> downloadExport(
            @RequestAttribute("userId") Integer userId,
            @PathVariable("id") Long id) {
        return exportService.downloadExport(userId, id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消导出任务")
    public Result<Void> cancelExport(
            @RequestAttribute("userId") Integer userId,
            @PathVariable("id") Long id) {
        return exportService.cancelExport(userId, id);
    }
}