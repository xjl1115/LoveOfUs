package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.constant.VipConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.mapper.VipOrderMapper;
import com.example.lovemap.model.dto.VipOrderDTO;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.entity.VipOrder;
import com.example.lovemap.model.vo.VipOrderVO;
import com.example.lovemap.service.AsyncMailService;
import com.example.lovemap.service.EmailNotificationService;
import com.example.lovemap.service.NotificationService;
import com.example.lovemap.service.QuotaService;
import com.example.lovemap.makeover.mapper.MakeoverQuotaGrantMapper;
import com.example.lovemap.service.VipPricingService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VipServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final VipOrderMapper vipOrderMapper = mock(VipOrderMapper.class);
    private final VipPricingService vipPricingService = mock(VipPricingService.class);
    private final VipServiceImpl service = new VipServiceImpl(
            userMapper,
            vipOrderMapper,
            vipPricingService,
            mock(StringRedisTemplate.class),
            mock(AsyncMailService.class),
            mock(NotificationService.class),
            mock(EmailNotificationService.class),
            mock(QuotaService.class),
            mock(MakeoverQuotaGrantMapper.class)
    );

    @Test
    void listOrdersShouldMapQuotaOrderWithoutVipTier() {
        User user = user(12L, 5L);
        VipOrder order = quotaOrder();
        when(userMapper.selectById(12)).thenReturn(user);
        when(vipOrderMapper.selectByUserOrGroup(12L, 5L)).thenReturn(List.of(order));

        Result<List<VipOrderVO>> result = service.listOrders(12);

        assertTrue(result.isSuccess());
        assertEquals("AI 约会建议", result.getData().get(0).getQuotaTypeName());
        assertEquals(3, result.getData().get(0).getQuotaCount());
    }

    @Test
    void cancelOrderShouldMapQuotaOrderWithoutVipTier() {
        User user = user(12L, 5L);
        VipOrder order = quotaOrder();
        when(userMapper.selectById(12)).thenReturn(user);
        when(vipOrderMapper.selectByOrderNo("QUOTA-ORDER")).thenReturn(order);
        when(vipOrderMapper.cancelPendingOrder(
                order.getId(), VipConstant.ORDER_STATUS_PENDING, VipConstant.ORDER_STATUS_CANCELED))
                .thenReturn(1);

        Result<VipOrderVO> result = service.cancelOrder(12, "QUOTA-ORDER");

        assertTrue(result.isSuccess());
        assertEquals("AI 约会建议", result.getData().getQuotaTypeName());
        assertEquals(VipConstant.ORDER_STATUS_CANCELED, result.getData().getStatus());
    }

    @Test
    void createQuotaOrderShouldCalculateDecimalPrice() {
        User user = user(12L, 5L);
        user.setPhone("13800000000");
        VipOrderDTO dto = new VipOrderDTO();
        dto.setOrderType(VipConstant.ORDER_TYPE_QUOTA);
        dto.setQuotaType(VipConstant.QUOTA_PRODUCT_DATE_PLAN);
        dto.setQuotaCount(3);
        when(vipPricingService.getQuotaUnitPrice(VipConstant.QUOTA_PRODUCT_DATE_PLAN))
                .thenReturn(new BigDecimal("0.3"));
        when(userMapper.selectById(12)).thenReturn(user);
        when(vipOrderMapper.selectPendingOrdersByGroup(12L, 5L, VipConstant.ORDER_STATUS_PENDING))
                .thenReturn(List.of());

        Result<Object> result = service.createOrder(12, dto);

        assertTrue(result.isSuccess());
        VipOrderVO order = (VipOrderVO) result.getData();
        assertEquals(0, new BigDecimal("0.9").compareTo(order.getPriceYuan()));
    }

    private static User user(Long id, Long groupId) {
        User user = new User();
        user.setId(id);
        user.setGroupId(groupId);
        return user;
    }

    private static VipOrder quotaOrder() {
        VipOrder order = new VipOrder();
        order.setId(100L);
        order.setOrderNo("QUOTA-ORDER");
        order.setUserId(12L);
        order.setGroupId(5L);
        order.setVipLevel(VipConstant.LEVEL_NORMAL);
        order.setOrderType(VipConstant.ORDER_TYPE_QUOTA);
        order.setQuotaType(VipConstant.QUOTA_PRODUCT_DATE_PLAN);
        order.setQuotaCount(3);
        order.setPriceYuan(new BigDecimal("0.9"));
        order.setStatus(VipConstant.ORDER_STATUS_PENDING);
        return order;
    }
}
