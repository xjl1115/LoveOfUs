package com.example.lovemap.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 顾问控制台：情侣任务保存 DTO
 */
@Data
public class CoupleTaskSaveDTO {

    private Long id;

    @NotBlank(message = "任务内容不能为空")
    @Size(max = 255, message = "任务内容长度不能超过 255")
    private String content;

    @Size(max = 500, message = "任务描述长度不能超过 500")
    private String description;

    @Size(max = 32, message = "图标长度不能超过 32")
    private String icon;

    @NotNull(message = "积分不能为空")
    @Min(value = 0, message = "积分不能为负")
    private Integer points;

    /** 0=禁用 1=启用 */
    @NotNull(message = "状态不能为空")
    private Integer status;

    /**
     * 完成方式（由哪个系统事件自动识别）
     * manual=需用户手动完成（不推荐）/ message_send=发送消息
     * photo_upload=上传照片 / date_plan_create=新建约会计划
     */
    @Pattern(regexp = "manual|message_send|photo_upload|date_plan_create",
            message = "完成方式取值不合法")
    private String completeType;

    /** 周期：daily/weekly/monthly */
    @Pattern(regexp = "daily|weekly|monthly", message = "周期取值不合法")
    private String periodType;

    /** 1=需手动领取积分 0=完成即发放 */
    private Integer claimRequired;

    private Integer sortOrder;
}
