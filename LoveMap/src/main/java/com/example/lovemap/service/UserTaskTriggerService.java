package com.example.lovemap.service;

/**
 * 情侣任务自动识别触发器
 *
 * 任务不再由用户手动标记完成：业务事件发生时（发消息 / 上传照片 / 新建约会计划），
 * 由本服务按任务的 complete_type 匹配并写入完成状态。
 *
 * 约定：实现必须自行吞掉异常，绝不能因任务识别失败影响主业务流程。
 */
public interface UserTaskTriggerService {

    /**
     * 上报业务事件
     *
     * @param userId       触发事件的用户 ID
     * @param completeType 事件类型，取值见 PointsConstant.COMPLETE_*
     */
    void onEvent(Integer userId, String completeType);
}
