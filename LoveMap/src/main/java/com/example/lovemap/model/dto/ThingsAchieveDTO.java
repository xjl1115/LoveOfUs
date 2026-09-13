package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标记完成 / 取消请求 DTO
 *
 * 标记完成时：必填 thingId，可选 note、photoIds，cancel 缺省为 false
 * 取消完成时：thingId + cancel=true（note / photoIds 忽略）
 */
@Data
public class ThingsAchieveDTO {

    /**
     * 事项 ID
     */
    @NotNull(message = "事项 ID 不能为空")
    private Long thingId;

    /**
     * 完成笔记（取消时忽略）
     */
    @Size(max = 500, message = "笔记长度不能超过 500 字")
    private String note;

    /**
     * 同时关联的照片 ID 列表（标记完成时可附带）
     */
    private java.util.List<Long> photoIds;

    /**
     * 是否取消完成：true-取消已完成，false/缺省-标记完成
     * <p>
     * 显式标识意图，避免「未填回忆」被误判为取消完成
     */
    private Boolean cancel;
}
