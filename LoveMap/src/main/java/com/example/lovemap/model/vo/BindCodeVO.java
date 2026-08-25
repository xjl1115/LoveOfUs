package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 绑定码信息VO
 */
@Data
public class BindCodeVO {

    /**
     * 绑定码，8位字母数字组合
     */
    private String code;

    /**
     * 当前用户是否已绑定伴侣
     */
    private Boolean isBound;
}
