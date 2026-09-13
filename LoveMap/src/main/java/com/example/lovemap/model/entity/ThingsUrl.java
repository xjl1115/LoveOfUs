package com.example.lovemap.model.entity;

import lombok.Data;

/**
 * 情侣必做事项图片URL池
 * 对应数据库表：things_url
 *
 * 表结构（保持现状，不做改动）：
 *   id  int PK
 *   url varchar(255)
 *
 * 关联通过中间表 things_url_rel（thing_id ↔ url_id ↔ group_id）
 */
@Data
public class ThingsUrl {

    /**
     * 主键
     */
    private Long id;

    /**
     * 图片URL
     */
    private String url;
}