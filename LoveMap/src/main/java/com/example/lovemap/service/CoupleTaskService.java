package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.CoupleTaskClaimDTO;
import com.example.lovemap.model.dto.CoupleTaskSaveDTO;
import com.example.lovemap.model.vo.CoupleTaskVO;

import java.util.List;

/**
 * 情侣任务 Service
 *
 * 任务完成由系统事件自动识别（见 UserTaskTriggerService），
 * 用户侧只需查询状态与领取积分，不再手动标记完成。
 */
public interface CoupleTaskService {

    /** 用户端：列出已启用任务，并合并该用户当前周期的任务状态 */
    Result<List<CoupleTaskVO>> listForUser(Integer userId);

    /** 用户端：领取任务积分（已完成待领取 → 已领取） */
    Result<CoupleTaskVO> claim(Integer userId, CoupleTaskClaimDTO dto);

    /** 顾问控制台：列出全部任务（含禁用） */
    Result<List<CoupleTaskVO>> listAll();

    /** 顾问控制台：保存 */
    Result<Long> save(CoupleTaskSaveDTO dto);

    /** 顾问控制台：删除 */
    Result<Void> delete(Long id);
}
