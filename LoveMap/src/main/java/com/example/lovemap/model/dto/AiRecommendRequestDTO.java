package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 推荐请求 DTO
 */
@Data
public class AiRecommendRequestDTO {

    @NotBlank(message = "推荐偏好不能为空")
    @Size(max = 500, message = "推荐偏好最多500字符")
    private String prompt;
}
