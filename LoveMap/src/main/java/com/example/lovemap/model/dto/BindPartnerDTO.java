package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * 绑定伴侣请求DTO
 */
@Data
public class BindPartnerDTO {

    /**
     * 伴侣提供的8位绑定码
     */
    private String partnerCode;
}
