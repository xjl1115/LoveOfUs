package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * 绑定码信息DTO
 */
@Data
public class BindCodeDTO {

    /**
     * 绑定码，8位字母数字组合
     */
    private String bindCode;

    /**
     * 当前用户是否已绑定伴侣
     */
    private Boolean isBound;
}
