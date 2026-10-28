package com.example.lovemap.service;

import com.example.lovemap.common.constant.QuotaType;
import com.example.lovemap.model.entity.User;

/**
 * 配额服务：导出与备份（每月）+ AI 推荐（每周），
 * 基础额度 + VIP 等级加成；额度耗尽抛 BusinessException(FORBIDDEN)。
 */
public interface QuotaService {

    /**
     * 消费一次配额，无剩余时抛异常。
     *
     * @param user      当前用户
     * @param quotaType {@link QuotaType} 1=导出 2=AI推荐
     */
    void consume(User user, int quotaType);

    /**
     * 查询当前周期剩余配额（不消费）。
     */
    int remaining(User user, int quotaType);

    /**
     * 配额展示分母：本周期基础额度（基础+VIP加成）+ 未用完的次卡次数，
     * 使 已用 = 分母 - 剩余 恰好等于本周期基础额度消耗；永久卡为 -1 表示不限量。
     */
    int limit(User user, int quotaType);
}
