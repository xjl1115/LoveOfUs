package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上传图片关联到事项 DTO
 *
 * 上传图片由 photo 模块负责（OSS 上传后拿到 url），
 * 这里仅负责把已有 URL 落地到 things_url 并关联到事项。
 */
@Data
public class ThingsPhotoUploadDTO {

    /**
     * 事项 ID
     */
    @NotNull(message = "事项 ID 不能为空")
    private Long thingId;

    /**
     * 图片 URL（OSS 已上传的访问地址）
     */
    @NotBlank(message = "图片 URL 不能为空")
    @Size(max = 255, message = "URL 长度不能超过 255")
    private String url;
}