package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收货地址
 * 对应数据库表：shipping_address
 *
 * 设计要点：
 *   - 地址为用户私有数据，按 user_id 隔离
 *   - is_default=1 的地址为默认收货地址，仅可有一个
 *   - 订单下单时快照地址信息，见 PointExchange 的 receiver_* / shipping_address
 */
@Data
public class ShippingAddress {

    private Long id;
    private Long userId;
    /** 收货人姓名 */
    private String receiverName;
    /** 收货人手机号 */
    private String receiverPhone;
    /** 省/直辖市 */
    private String province;
    /** 市/区 */
    private String city;
    /** 详细地址 */
    private String detailAddress;
    /** 是否默认：0=否 1=是 */
    private Integer isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}