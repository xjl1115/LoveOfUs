package com.example.lovemap.service;

import com.example.lovemap.model.entity.MoodLog;

import java.time.LocalDate;
import java.util.List;

public interface MoodLogService {

    MoodLog record(Long groupId, Long userId, String mood, Integer moodScore, String note, LocalDate logDate);

    List<MoodLog> getByDate(Long groupId, LocalDate date);

    List<MoodLog> getInRange(Long groupId, LocalDate start, LocalDate end);
}