package com.example.lovemap.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出任务记录 VO
 * 与前端 ExportRecord 接口对齐
 */
@Data
public class ExportRecordVO {

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private String startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private String endDate;

    private Integer photoCount;

    private String format;

    private String status;

    private String filePath;

    private Long fileSize;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime completedAt;
}