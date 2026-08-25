package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 导出任务记录实体
 */
@Data
public class ExportRecord {

    private Long id;

    private Long userId;

    /**
     * 导出格式：zip / pdf
     */
    private String format;

    /**
     * 开始日期
     */
    private LocalDate startDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

    /**
     * 照片数量
     */
    private Integer photoCount;

    /**
     * 导出文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 任务状态：pending/processing/completed/failed
     */
    private String status;

    /**
     * 导出选项（JSON）
     */
    private String options;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}