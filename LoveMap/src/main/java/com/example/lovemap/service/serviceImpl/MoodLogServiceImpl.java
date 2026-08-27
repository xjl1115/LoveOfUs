package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.mapper.MoodLogMapper;
import com.example.lovemap.model.entity.MoodLog;
import com.example.lovemap.service.MoodLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoodLogServiceImpl implements MoodLogService {

    private final MoodLogMapper moodLogMapper;

    @Override
    public MoodLog record(Long groupId, Long userId, String mood, Integer moodScore, String note, LocalDate logDate) {
        // 同一天允许覆盖（用 upsert）
        MoodLog exist = new MoodLog();
        exist.setGroupId(groupId);
        exist.setUserId(userId);
        exist.setMood(mood);
        exist.setMoodScore(moodScore);
        exist.setNote(note);
        exist.setLogDate(logDate == null ? LocalDate.now() : logDate);
        moodLogMapper.insert(exist);
        return exist;
    }

    @Override
    public List<MoodLog> getByDate(Long groupId, LocalDate date) {
        return moodLogMapper.selectByGroupAndDate(groupId, date);
    }

    @Override
    public List<MoodLog> getInRange(Long groupId, LocalDate start, LocalDate end) {
        return moodLogMapper.selectByGroupInRange(groupId, start, end);
    }
}