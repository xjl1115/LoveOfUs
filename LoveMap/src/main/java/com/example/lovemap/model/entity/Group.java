package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情侣关系表实体
 * 对应数据库表：group
 */
@Data
public class Group {

    /**
     * 主键（自增）
     */
    private Integer id;

    /**
     * 情侣 UUID
     */
    private String groupId;

    /**
     * 用户1 ID
     */
    private Integer user1Id;

    /**
     * 用户2 ID
     */
    private Integer user2Id;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}