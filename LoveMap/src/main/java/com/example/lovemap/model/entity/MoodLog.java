package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 心情打卡
 */
@Data
public class MoodLog {
    private Long id;
    private Long groupId;
    private Long userId;
    private String mood;
    private Integer moodScore;
    private String note;
    private LocalDate logDate;
    private LocalDateTime createdAt;
}