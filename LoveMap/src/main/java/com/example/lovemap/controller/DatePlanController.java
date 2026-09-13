package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AiRecommendRequestDTO;
import com.example.lovemap.model.dto.DatePlanCreateDTO;
import com.example.lovemap.model.dto.DatePlanStatusDTO;
import com.example.lovemap.model.dto.DatePlanUpdateDTO;
import com.example.lovemap.model.vo.AiRecommendVO;
import com.example.lovemap.model.vo.DatePlanVO;
import com.example.lovemap.service.DatePlanService;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 约会计划 Controller
 */
@RestController
@Slf4j
@Tag(name = "约会计划", description = "约会计划管理接口")
@RequestMapping("/date-plans")
@RequiredArgsConstructor
public class DatePlanController {

    private final DatePlanService datePlanService;

    @GetMapping
    @Operation(summary = "查询约会计划列表")
    public Result<List<DatePlanVO>> listDatePlans(@RequestAttribute("userId") Integer userId) {
        return datePlanService.listDatePlans(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询约会计划详情")
    public Result<DatePlanVO> getDatePlanDetail(@RequestAttribute("userId") Integer userId,
                                                 @PathVariable("id") Long id) {
        return datePlanService.getDatePlanDetail(userId, id);
    }

    @PostMapping
    @Operation(summary = "创建约会计划")
    public Result<DatePlanVO> createDatePlan(@RequestAttribute("userId") Integer userId,
                                              @Valid @RequestBody DatePlanCreateDTO dto) {
        return datePlanService.createDatePlan(userId, dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新约会计划")
    public Result<DatePlanVO> updateDatePlan(@RequestAttribute("userId") Integer userId,
                                              @PathVariable("id") Long id,
                                              @Valid @RequestBody DatePlanUpdateDTO dto) {
        return datePlanService.updateDatePlan(userId, id, dto);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "变更约会计划状态")
    public Result<DatePlanVO> updateDatePlanStatus(@RequestAttribute("userId") Integer userId,
                                                    @PathVariable("id") Long id,
                                                    @Valid @RequestBody DatePlanStatusDTO dto) {
        return datePlanService.updateDatePlanStatus(userId, id, dto);
    }

    @PostMapping("/{id}/photos")
    @Operation(summary = "上传约会照片")
    public Result<DatePlanVO> uploadDatePlanPhoto(@RequestAttribute("userId") Integer userId,
                                                   @PathVariable("id") Long id,
                                                   @RequestParam("file") MultipartFile file) {
        return datePlanService.uploadDatePlanPhoto(userId, id, file);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除约会计划")
    public Result<Void> deleteDatePlan(@RequestAttribute("userId") Integer userId,
                                        @PathVariable("id") Long id) {
        return datePlanService.deleteDatePlan(userId, id);
    }

    @PostMapping("/recommend")
    @Operation(summary = "AI 推荐约会计划")
    public Result<AiRecommendVO> recommendDatePlans(@RequestAttribute("userId") Integer userId,
                                                     @Valid @RequestBody AiRecommendRequestDTO dto) {
        return datePlanService.recommendDatePlans(userId, dto);
    }
}
