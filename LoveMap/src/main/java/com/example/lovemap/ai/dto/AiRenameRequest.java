package com.example.lovemap.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重命名 AI 会话请求
 */
@Data
public class AiRenameRequest {

    @NotBlank
    @Size(max = 100, message = "标题不能超过 100 字")
    private String title;
}