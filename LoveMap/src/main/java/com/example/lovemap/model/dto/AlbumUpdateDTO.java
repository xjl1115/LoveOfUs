package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * 更新相册 DTO
 */
@Data
public class AlbumUpdateDTO {

    /**
     * 相册名称
     */
    private String name;

    /**
     * 相册描述
     */
    private String description;
}
