package com.example.lovemap.service;

import com.example.lovemap.model.entity.Anniversary;
import com.example.lovemap.model.entity.AnniversaryReminder;

import java.util.List;

public interface AnniversaryReminderService {

    /**
     * 为纪念日生成提醒计划（按 anniversary.remind_days）
     */
    List<AnniversaryReminder> planReminders(Long groupId, Anniversary anniversary);

    /**
     * 查询纪念日的提醒列表
     */
    List<AnniversaryReminder> listByAnniversary(Long anniversaryId);

    /**
     * 删除纪念日的所有提醒
     */
    int deleteByAnniversary(Long anniversaryId);
}