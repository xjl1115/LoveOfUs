package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.PointExchangeDTO;
import com.example.lovemap.model.entity.PointExchange;
import com.example.lovemap.model.vo.PointOrderVO;
import com.example.lovemap.model.vo.PointProductVO;
import com.example.lovemap.service.PointProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 积分商品 Controller（用户端）
 */
@RestController
@Tag(name = "积分商城", description = "积分商品兑换")
@RequestMapping("/point-products")
@RequiredArgsConstructor
public class PointProductController {

    private final PointProductService pointProductService;

    /** 列出已上架商品（积分商城页） */
    @GetMapping
    @Operation(summary = "列出已上架积分商品")
    public Result<List<PointProductVO>> list() {
        return pointProductService.listOnShelf();
    }

    /** 兑换 */
    @PostMapping("/exchange")
    @Operation(summary = "用积分兑换商品")
    public Result<PointOrderVO> exchange(@RequestAttribute("userId") Integer userId,
                                         @Valid @RequestBody PointExchangeDTO dto) {
        return pointProductService.exchange(userId, dto);
    }

    /** 当前用户最近兑换记录 */
    @GetMapping("/my-exchanges")
    @Operation(summary = "我的兑换记录")
    public Result<List<PointExchange>> myExchanges(@RequestAttribute("userId") Integer userId) {
        return pointProductService.recentExchanges(userId);
    }
}
