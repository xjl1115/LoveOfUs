package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.VipConstant;
import com.example.lovemap.model.dto.CoupleTaskSaveDTO;
import com.example.lovemap.model.dto.PointProductSaveDTO;
import com.example.lovemap.model.entity.PointExchange;
import com.example.lovemap.model.vo.CoupleTaskVO;
import com.example.lovemap.model.vo.PointProductVO;
import com.example.lovemap.service.CoupleTaskService;
import com.example.lovemap.service.PointProductService;
import com.example.lovemap.service.VipService;
import com.example.lovemap.utils.AliyunOSSUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 顾问控制台：积分商品 / 情侣任务管理
 *
 * 鉴权复用 VipService.isValidAdminToken（X-Vip-Admin-Token 头）
 * 与 /vip/admin/** 同源，避免在顾问控制台维护多套密钥
 */
@Slf4j
@RestController
@Tag(name = "顾问控制台-积分与任务", description = "积分商品与情侣任务的 CRUD（顾问控制台）")
@RequestMapping("/admin/points")
@RequiredArgsConstructor
public class AdminPointsController {

    private final PointProductService pointProductService;
    private final CoupleTaskService coupleTaskService;
    private final VipService vipService;
    private final AliyunOSSUtils aliyunOSSUtils;

    private boolean requireAdmin(String token) {
        if (!vipService.isValidAdminToken(token)) {
            log.warn("[顾问控制台-积分/任务] 鉴权失败");
            return false;
        }
        return true;
    }

    // ============== 积分商品 ==============

    @GetMapping("/products")
    @Operation(summary = "列出全部积分商品（含下架）")
    public Result<List<PointProductVO>> listProducts(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        return pointProductService.listAll();
    }

    @PostMapping("/products")
    @Operation(summary = "新建积分商品")
    public Result<Long> createProduct(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @Valid @RequestBody PointProductSaveDTO dto) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        dto.setId(null);
        return pointProductService.save(dto);
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "更新积分商品")
    public Result<Long> updateProduct(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @PathVariable Long id,
            @Valid @RequestBody PointProductSaveDTO dto) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        dto.setId(id);
        return pointProductService.save(dto);
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "删除积分商品")
    public Result<Void> deleteProduct(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @PathVariable Long id) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        return pointProductService.delete(id);
    }

    @PostMapping("/upload")
    @Operation(summary = "上传商品图片到阿里云 OSS，返回可访问 URL")
    public Result<String> uploadProductImage(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @RequestParam("file") MultipartFile file) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        if (file == null || file.isEmpty()) {
            return Result.badRequest("请选择要上传的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return Result.badRequest("仅支持上传图片文件");
        }
        // 对象键：固定目录 + UUID，避免文件名冲突与中文名乱码
        String ext = resolveExtension(file.getOriginalFilename());
        String objectKey = "LoveMap/point_product/" + UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            String url = aliyunOSSUtils.asyncUploadFile(file, objectKey);
            if (url == null || url.isBlank()) {
                return Result.error(ResultCode.INTERNAL_SERVER_ERROR, "图片上传失败，请重试");
            }
            return Result.success(url);
        } catch (Exception e) {
            log.error("[顾问控制台-积分/任务] 商品图片上传失败, objectKey={}", objectKey, e);
            return Result.error(ResultCode.INTERNAL_SERVER_ERROR, "图片上传失败，请重试");
        }
    }

    // ============== 积分商品订单（含金额商品需顾问确认收款） ==============

    @GetMapping("/orders/pending")
    @Operation(summary = "列出待确认收款的积分商品订单")
    public Result<List<PointExchange>> listPendingOrders(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        return pointProductService.listPendingOrders();
    }

    @PostMapping("/orders/{orderNo}/activate")
    @Operation(summary = "确认收款并发放（扣积分 → 扣库存 → 置为已发放）")
    public Result<PointExchange> activateOrder(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @PathVariable String orderNo) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        return pointProductService.activateOrder(orderNo);
    }

    @PostMapping("/orders/{orderNo}/cancel")
    @Operation(summary = "取消待确认的积分商品订单")
    public Result<Void> cancelOrder(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @PathVariable String orderNo) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        return pointProductService.cancelOrder(orderNo);
    }

    /**
     * 从原始文件名解析扩展名（含点号），无法识别时返回空串
     */
    private String resolveExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(dot).toLowerCase(Locale.ROOT);
        // 仅保留常见图片后缀，防止上传恶意扩展名
        return switch (ext) {
            case ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp" -> ext;
            default -> "";
        };
    }

    // ============== 情侣任务 ==============

    @GetMapping("/tasks")
    @Operation(summary = "列出全部情侣任务（含禁用）")
    public Result<List<CoupleTaskVO>> listTasks(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        return coupleTaskService.listAll();
    }

    @PostMapping("/tasks")
    @Operation(summary = "新建情侣任务")
    public Result<Long> createTask(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @Valid @RequestBody CoupleTaskSaveDTO dto) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        dto.setId(null);
        return coupleTaskService.save(dto);
    }

    @PutMapping("/tasks/{id}")
    @Operation(summary = "更新情侣任务")
    public Result<Long> updateTask(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @PathVariable Long id,
            @Valid @RequestBody CoupleTaskSaveDTO dto) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        dto.setId(id);
        return coupleTaskService.save(dto);
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "删除情侣任务")
    public Result<Void> deleteTask(
            @RequestHeader(value = VipConstant.ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @PathVariable Long id) {
        if (!requireAdmin(adminToken)) return Result.forbidden("无权限操作");
        return coupleTaskService.delete(id);
    }
}
