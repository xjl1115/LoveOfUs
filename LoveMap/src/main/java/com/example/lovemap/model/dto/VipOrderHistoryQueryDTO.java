package com.example.lovemap.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 历史开通订单查询条件（顾问侧）
 */
@Data
public class VipOrderHistoryQueryDTO {

    /** 订单号，精确匹配 */
    private String orderNo;

    /** 下单人手机号，精确匹配 */
    private String phone;

    /** 下单人邮箱，精确匹配 */
    private String email;

    /** 下单年份，精确到年，如 2026 */
    private Integer year;

    /** 下单月份，1-12；与 year 组合精确到月 */
    @Min(value = 1, message = "月份必须在 1-12 之间")
    @Max(value = 12, message = "月份必须在 1-12 之间")
    private Integer month;

    /**
     * 订单状态过滤：null/缺省=全部；0=待开通；1=已开通；2=已取消
     */
    private Integer status;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码必须从 1 开始")
    private Integer page = 1;

    /** 每页大小 */
    @Min(value = 1, message = "每页大小必须大于 0")
    @Max(value = 100, message = "每页大小不能超过 100")
    private Integer size = 20;

    /** SQL 偏移量，由 Service 层根据 page/size 计算后注入 */
    private Integer offset;

    /**
     * 计算 SQL 偏移量：(page - 1) * size
     */
    public Integer getOffset() {
        int p = page == null ? 1 : page;
        int s = size == null ? 20 : size;
        return (p - 1) * s;
    }
}
