package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 绑定伴侣结果VO
 */
@Data
public class BindResultVO {

    /**
     * 是否绑定成功
     */
    private Boolean isBound;

    /**
     * 伴侣信息
     */
    private PartnerVO partner;

    /**
     * 关系开始日期
     */
    private LocalDate relationshipStart;

    /**
     * 在一起的天数
     */
    private Integer daysTogether;
}
