package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.VipConstant;
import com.example.lovemap.model.dto.VipBatchActivateDTO;
import com.example.lovemap.model.dto.VipOrderDTO;
import com.example.lovemap.model.vo.VipBatchActivateVO;
import com.example.lovemap.model.vo.VipOrderVO;
import com.example.lovemap.model.vo.VipPendingOrderVO;
import com.example.lovemap.model.vo.VipTierVO;
import com.example.lovemap.service.VipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * VIP 会员 Controller
 */
@RestController
@Slf4j
@Tag(name = "VIP 会员", description = "VIP 档位查询与下单接口")
@RequestMapping("/vip")
@RequiredArgsConstructor
public class VipController {

    private final VipService vipService;

    @GetMapping("/tiers")
    @Operation(summary = "查询 VIP 档位与权益")
    public Result<List<VipTierVO>> listTiers() {
        return vipService.listTiers();
    }

    @PostMapping("/orders")
    @Operation(summary = "提交 VIP 下单（仅登记订单，待顾问收款后开通）")
    public Result<VipOrderVO> createOrder(@RequestAttribute("userId") Integer userId,
                                          @Valid @RequestBody VipOrderDTO dto) {
        return vipService.createOrder(userId, dto);
    }

    @GetMapping("/orders")
    @Operation(summary = "查询我的账单（按情侣组查询全部订单）")
    public Result<List<VipOrderVO>> listOrders(@RequestAttribute("userId") Integer userId) {
        return vipService.listOrders(userId);
    }

    @PostMapping("/orders/{orderNo}/cancel")
    @Operation(summary = "取消待开通的 VIP 订单")
    public Result<VipOrderVO> cancelOrder(@RequestAttribute("userId") Integer userId,
                                          @PathVariable("orderNo") String orderNo) {
        return vipService.cancelOrder(userId, orderNo);
    }

    @GetMapping("/admin/orders/pending")
    @Operation(summary = "查询待开通订单（顾问收款前核对）")
    public Result<List<VipPendingOrderVO>> listPendingOrders(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        if (!vipService.isValidAdminToken(adminToken)) {
            log.warn("[VIP] 待开通订单查询密钥校验失败");
            return Result.forbidden("无权限操作");
        }
        return vipService.listPendingOrders();
    }

    @PostMapping("/admin/orders/{orderNo}/activate")
    @Operation(summary = "顾问收款后开通 VIP")
    public Result<VipOrderVO> activateOrder(
            @PathVariable("orderNo") String orderNo,
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        if (!vipService.isValidAdminToken(adminToken)) {
            log.warn("[VIP] 开通接口密钥校验失败, orderNo={}", orderNo);
            return Result.forbidden("无权限操作");
        }
        return vipService.activateOrder(orderNo);
    }

    @PostMapping("/admin/orders/batch-activate")
    @Operation(summary = "批量开通 VIP（逐单尝试，单笔失败不影响其他订单）")
    public Result<VipBatchActivateVO> batchActivateOrders(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @Valid @RequestBody VipBatchActivateDTO dto) {
        if (!vipService.isValidAdminToken(adminToken)) {
            log.warn("[VIP] 批量开通密钥校验失败");
            return Result.forbidden("无权限操作");
        }

        List<VipBatchActivateVO.Item> results = new ArrayList<>();
        int successCount = 0;
        for (String orderNo : dto.getOrderNos()) {
            // 逐单调用：每单各自成事务，一笔失败不影响其他订单
            Result<VipOrderVO> result = vipService.activateOrder(orderNo);
            boolean success = result.getCode() == ResultCode.SUCCESS.getCode();

            VipBatchActivateVO.Item item = new VipBatchActivateVO.Item();
            item.setOrderNo(orderNo);
            item.setSuccess(success);
            item.setMessage(result.getMessage());
            if (success) {
                successCount++;
                item.setVipLevel(result.getData().getVipLevel());
                item.setVipExpireAt(result.getData().getVipExpireAt());
            }
            results.add(item);
        }

        VipBatchActivateVO vo = new VipBatchActivateVO();
        vo.setTotal(results.size());
        vo.setSuccessCount(successCount);
        vo.setFailCount(results.size() - successCount);
        vo.setResults(results);

        log.info("[VIP] 批量开通完成，共 {} 单，成功 {} 单，失败 {} 单", vo.getTotal(), vo.getSuccessCount(), vo.getFailCount());
        return Result.success(vo);
    }
}
