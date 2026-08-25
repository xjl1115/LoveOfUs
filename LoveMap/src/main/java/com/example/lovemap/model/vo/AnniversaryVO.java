package com.example.lovemap.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 纪念日视图对象
 */
@Data
public class AnniversaryVO {

    private Long id;

    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate anniversaryDate;

    private Boolean isRecurring;

    private Integer remindDays;

    private String description;

    /**
     * 距离下次纪念日的天数（计算字段）
     */
    private Long daysUntil;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 计算距离下次纪念日的天数
     */
    public void calculateDaysUntil() {
        if (anniversaryDate == null) {
            this.daysUntil = null;
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate nextAnniversary = anniversaryDate;

        // 如果是重复性纪念日，计算下一次的日期
        if (Boolean.TRUE.equals(isRecurring)) {
            nextAnniversary = anniversaryDate.withYear(today.getYear());
            if (nextAnniversary.isBefore(today) || nextAnniversary.isEqual(today)) {
                nextAnniversary = nextAnniversary.plusYears(1);
            }
        }

        this.daysUntil = ChronoUnit.DAYS.between(today, nextAnniversary);
    }
}