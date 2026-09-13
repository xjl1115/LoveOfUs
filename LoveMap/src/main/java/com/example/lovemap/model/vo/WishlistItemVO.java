package com.example.lovemap.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 心愿清单视图对象
 */
@Data
public class WishlistItemVO {

    private Long id;

    private Long groupId;

    private String title;

    private String description;

    private String category;

    private String icon;

    private Integer priority;

    private Integer targetValue;

    private Integer currentValue;

    private String unit;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    private Integer needBothConfirm;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime achievedAt;

    private String achievedNote;

    /** 达成纪念照片 URL（1 张） */
    private String achievedPhotoUrl;

    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
