package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 相册创建DTO
 */
@Data
public class AlbumCreateDTO {

    /**
     * 相册名称
     */
    @NotBlank(message = "相册名称不能为空")
    private String name;

    /**
     * 相册描述
     */
    private String description;
}
