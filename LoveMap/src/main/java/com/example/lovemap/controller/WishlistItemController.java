package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AiRecommendRequestDTO;
import com.example.lovemap.model.dto.WishlistItemCreateDTO;
import com.example.lovemap.model.dto.WishlistItemStatusDTO;
import com.example.lovemap.model.dto.WishlistItemUpdateDTO;
import com.example.lovemap.model.dto.WishlistProgressDTO;
import com.example.lovemap.model.vo.AiRecommendVO;
import com.example.lovemap.model.vo.WishlistItemVO;
import com.example.lovemap.service.WishCardShareService;
import com.example.lovemap.service.WishlistItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 心愿清单 Controller
 */
@RestController
@Slf4j
@Tag(name = "心愿清单", description = "心愿清单管理接口")
@RequestMapping("/wishlist-items")
@RequiredArgsConstructor
public class WishlistItemController {

    private final WishlistItemService wishlistItemService;
    private final WishCardShareService wishCardShareService;

    @GetMapping
    @Operation(summary = "查询心愿清单列表")
    public Result<List<WishlistItemVO>> listWishlistItems(@RequestAttribute("userId") Integer userId) {
        return wishlistItemService.listWishlistItems(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询心愿项详情")
    public Result<WishlistItemVO> getWishlistItemDetail(@RequestAttribute("userId") Integer userId,
                                                         @PathVariable("id") Long id) {
        return wishlistItemService.getWishlistItemDetail(userId, id);
    }

    @PostMapping
    @Operation(summary = "创建心愿项")
    public Result<WishlistItemVO> createWishlistItem(@RequestAttribute("userId") Integer userId,
                                                      @Valid @RequestBody WishlistItemCreateDTO dto) {
        return wishlistItemService.createWishlistItem(userId, dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新心愿项")
    public Result<WishlistItemVO> updateWishlistItem(@RequestAttribute("userId") Integer userId,
                                                      @PathVariable("id") Long id,
                                                      @Valid @RequestBody WishlistItemUpdateDTO dto) {
        return wishlistItemService.updateWishlistItem(userId, id, dto);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "变更心愿项状态")
    public Result<WishlistItemVO> updateWishlistItemStatus(@RequestAttribute("userId") Integer userId,
                                                            @PathVariable("id") Long id,
                                                            @Valid @RequestBody WishlistItemStatusDTO dto) {
        return wishlistItemService.updateWishlistItemStatus(userId, id, dto);
    }

    @PutMapping("/{id}/progress")
    @Operation(summary = "调整心愿进度（+1 / -1）")
    public Result<WishlistItemVO> adjustWishlistItemProgress(@RequestAttribute("userId") Integer userId,
                                                             @PathVariable("id") Long id,
                                                             @Valid @RequestBody WishlistProgressDTO dto) {
        return wishlistItemService.adjustWishlistItemProgress(userId, id, dto);
    }

    @PostMapping("/{id}/photo")
    @Operation(summary = "上传心愿纪念照片（1 张）")
    public Result<WishlistItemVO> uploadWishlistItemPhoto(@RequestAttribute("userId") Integer userId,
                                                           @PathVariable("id") Long id,
                                                           @RequestParam("file") MultipartFile file) {
        return wishlistItemService.uploadWishlistItemPhoto(userId, id, file);
    }

    @GetMapping("/{id}/photo/raw")
    @Operation(summary = "读取心愿纪念照片（服务端同域转发，供卡片导出）")
    public ResponseEntity<Resource> loadWishlistItemPhoto(@RequestAttribute("userId") Integer userId,
                                                           @PathVariable("id") Long id) {
        return wishlistItemService.loadWishlistItemPhoto(userId, id);
    }

    @PostMapping(value = "/{id}/card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "把心愿记录卡片发给伴侣")
    public Result<Long> shareWishCard(@RequestAttribute("userId") Integer userId,
                                       @PathVariable("id") Long id,
                                       @RequestParam("image") MultipartFile image,
                                       @RequestParam(value = "note", required = false) String note) {
        return Result.success(wishCardShareService.share(userId, id, image, note));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除心愿项")
    public Result<Void> deleteWishlistItem(@RequestAttribute("userId") Integer userId,
                                            @PathVariable("id") Long id) {
        return wishlistItemService.deleteWishlistItem(userId, id);
    }

    @PostMapping("/recommend")
    @Operation(summary = "AI 推荐心愿清单")
    public Result<AiRecommendVO> recommendWishlistItems(@RequestAttribute("userId") Integer userId,
                                                         @Valid @RequestBody AiRecommendRequestDTO dto) {
        return wishlistItemService.recommendWishlistItems(userId, dto);
    }
}
