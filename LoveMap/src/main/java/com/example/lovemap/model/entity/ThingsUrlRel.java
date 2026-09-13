package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事项与图片关联表
 * 对应数据库表：things_url_rel（新增）
 *
 * 一张照片可关联到多个事项（通过中间表实现 M:N），
 * 但当前业务只支持 1:N（一张图关联一个事项）。
 */
@Data
public class ThingsUrlRel {

    /**
     * 主键
     */
    private Long id;

    /**
     * 事项ID（关联 things.id）
     */
    private Long thingId;

    /**
     * 图片URL ID（关联 things_url.id）
     */
    private Long urlId;

    /**
     * 群组ID（情侣共享，便于按 group 查询）
     */
    private Long groupId;

    /**
     * 上传人用户ID
     */
    private Long uploadedBy;

    /**
     * 上传时间
     */
    private LocalDateTime createdAt;
}