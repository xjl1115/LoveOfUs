package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.mapper.AnniversaryReminderMapper;
import com.example.lovemap.model.entity.Anniversary;
import com.example.lovemap.model.entity.AnniversaryReminder;
import com.example.lovemap.service.AnniversaryReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnniversaryReminderServiceImpl implements AnniversaryReminderService {

    private final AnniversaryReminderMapper reminderMapper;

    /** 默认提醒天数（纪念日未设置时使用） */
    private static final List<Integer> DEFAULT_REMIND_DAYS = Arrays.asList(7, 3, 1);

    @Override
    public List<AnniversaryReminder> planReminders(Long groupId, Anniversary anniversary) {
        if (anniversary == null || anniversary.getId() == null) return List.of();
        List<Integer> remindDays;
        if (anniversary.getRemindDays() != null && anniversary.getRemindDays() > 0) {
            remindDays = List.of(anniversary.getRemindDays());
        } else {
            remindDays = DEFAULT_REMIND_DAYS;
        }
        List<AnniversaryReminder> result = new ArrayList<>();
        LocalDate annivDate = anniversary.getAnniversaryDate();
        for (Integer d : remindDays) {
            AnniversaryReminder r = new AnniversaryReminder();
            r.setGroupId(groupId);
            r.setAnniversaryId(anniversary.getId());
            r.setRemindDays(d);
            // 非周期性：今年；周期性：明年
            LocalDate today = LocalDate.now();
            LocalDate base = annivDate;
            if (Boolean.TRUE.equals(anniversary.getIsRecurring())) {
                // 计算本年提醒日：若已过则顺延到次年
                LocalDate thisYear = annivDate.withYear(today.getYear());
                base = !thisYear.isBefore(today) ? thisYear : thisYear.plusYears(1);
            }
            r.setRemindDate(base.minusDays(d));
            r.setIsSent(0);
            reminderMapper.insert(r);
            result.add(r);
        }
        return result;
    }

    @Override
    public List<AnniversaryReminder> listByAnniversary(Long anniversaryId) {
        return reminderMapper.selectByAnniversary(anniversaryId);
    }

    @Override
    public int deleteByAnniversary(Long anniversaryId) {
        return reminderMapper.deleteByAnniversary(anniversaryId);
    }
}