package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.ThingsAchieveDTO;
import com.example.lovemap.model.dto.ThingsPhotoUploadDTO;
import com.example.lovemap.model.vo.ThingsListVO;
import com.example.lovemap.model.vo.ThingsPhotoVO;
import com.example.lovemap.model.vo.ThingsStatsVO;
import com.example.lovemap.service.ThingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 情侣必做 100 件事 Controller
 */
@RestController
@Slf4j
@Tag(name = "情侣必做 100 件事", description = "必做 100 件事相关接口")
@RequestMapping("/things")
@RequiredArgsConstructor
public class ThingsController {

    private final ThingsService thingsService;

    /**
     * 获取 100 件事列表（含完成状态、照片数）
     */
    @GetMapping
    @Operation(summary = "获取 100 件事列表")
    public Result<List<ThingsListVO>> list(@RequestAttribute("userId") Integer userId) {
        return thingsService.listThings(userId);
    }

    /**
     * 进度统计
     */
    @GetMapping("/stats")
    @Operation(summary = "获取完成进度统计")
    public Result<ThingsStatsVO> stats(@RequestAttribute("userId") Integer userId) {
        return thingsService.getStats(userId);
    }

    /**
     * 标记完成 / 取消完成
     *   - note 与 photoIds 都为空时 = 取消
     *   - 否则 = 标记完成
     */
    @PostMapping("/achieve")
    @Operation(summary = "标记完成 / 取消完成")
    public Result<Void> achieve(@RequestAttribute("userId") Integer userId,
                                @Valid @RequestBody ThingsAchieveDTO dto) {
        return thingsService.achieve(userId, dto);
    }

    /**
     * 获取事项关联的图片列表
     */
    @GetMapping("/{thingId}/photos")
    @Operation(summary = "获取事项关联的图片")
    public Result<List<ThingsPhotoVO>> listPhotos(@RequestAttribute("userId") Integer userId,
                                                  @PathVariable Long thingId) {
        return thingsService.listPhotos(userId, thingId);
    }

    /**
     * 上传一张图片关联到事项
     */
    @PostMapping("/photos")
    @Operation(summary = "上传图片关联到事项")
    public Result<ThingsPhotoVO> uploadPhoto(@RequestAttribute("userId") Integer userId,
                                             @Valid @RequestBody ThingsPhotoUploadDTO dto) {
        return thingsService.uploadPhoto(userId, dto);
    }
}