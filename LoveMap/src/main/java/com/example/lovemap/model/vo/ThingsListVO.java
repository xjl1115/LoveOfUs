package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情侣必做 100 件事列表项 VO
 *
 * 字段说明：
 *   id           事项 ID
 *   order        排序（前端展示 001-100）
 *   title        事项标题
 *   description  事项描述
 *   coverEmoji   封面 emoji（无真实图片占位）
 *   achievedAt  完成时间（未完成则为 null）
 *   achievedNote 完成笔记（未完成则为 null）
 *   photoCount   该事项关联的图片数量
 */
@Data
public class ThingsListVO {

    /**
     * 事项 ID
     */
    private Long id;

    /**
     * 排序（001-100）
     */
    private Integer order;

    /**
     * 标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 左侧图标（emoji 文本，与 coverEmoji 共享字段，但仅作为卡片左侧小图标使用）
     */
    private String icon;

    /**
     * 封面 emoji（兼容旧字段；与 icon 取一即可）
     */
    private String coverEmoji;

    /**
     * 完成时间（未完成为 null）
     */
    private LocalDateTime achievedAt;

    /**
     * 完成笔记
     */
    private String achievedNote;

    /**
     * 关联照片数量
     */
    private Integer photoCount;
}