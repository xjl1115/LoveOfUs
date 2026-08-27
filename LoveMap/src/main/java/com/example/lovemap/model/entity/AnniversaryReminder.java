package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 纪念日提醒计划
 */
@Data
public class AnniversaryReminder {
    private Long id;
    private Long groupId;
    private Long anniversaryId;
    private Integer remindDays;
    private LocalDate remindDate;
    private Integer isSent;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}