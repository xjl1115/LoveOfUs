package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 收货地址保存 DTO
 */
@Data
public class ShippingAddressDTO {

    /** 收货人姓名 */
    @NotBlank(message = "收货人不能为空")
    @Size(max = 50, message = "收货人姓名过长")
    private String receiverName;

    /** 收货人手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String receiverPhone;

    /** 省/直辖市 */
    @NotBlank(message = "请选择省份")
    @Size(max = 50, message = "省份过长")
    private String province;

    /** 市/区 */
    @NotBlank(message = "请选择城市")
    @Size(max = 50, message = "城市过长")
    private String city;

    /** 详细地址 */
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址过长")
    private String detailAddress;

    /** 是否默认：0=否 1=是 */
    private Integer isDefault;
}