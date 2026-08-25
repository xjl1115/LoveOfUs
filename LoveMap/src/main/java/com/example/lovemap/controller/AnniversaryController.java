package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AnniversaryDTO;
import com.example.lovemap.model.vo.AnniversaryVO;
import com.example.lovemap.service.AnniversaryService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@Tag(name = "纪念日管理", description = "纪念日管理接口")
@RequestMapping("/anniversaries")
@RequiredArgsConstructor
public class AnniversaryController {

    private final AnniversaryService anniversaryService;

    @GetMapping
    @Operation(summary = "查询纪念日列表")
    public Result<List<AnniversaryVO>> listAnniversaries(@RequestAttribute("userId") Integer userId) {
        return anniversaryService.listAnniversaries(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询纪念日详情")
    public Result<AnniversaryVO> getAnniversaryDetail(@RequestAttribute("userId") Integer userId,
                                                       @PathVariable("id") Long id) {
        return anniversaryService.getAnniversaryDetail(userId, id);
    }

    @PostMapping
    @Operation(summary = "创建纪念日")
    public Result<AnniversaryVO> createAnniversary(@RequestAttribute("userId") Integer userId,
                                                    @Valid @RequestBody AnniversaryDTO dto) {
        return anniversaryService.createAnniversary(userId, dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改纪念日")
    public Result<AnniversaryVO> updateAnniversary(@RequestAttribute("userId") Integer userId,
                                                    @PathVariable("id") Long id,
                                                    @Valid @RequestBody AnniversaryDTO dto) {
        return anniversaryService.updateAnniversary(userId, id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除纪念日")
    public Result<Void> deleteAnniversary(@RequestAttribute("userId") Integer userId,
                                           @PathVariable("id") Long id) {
        return anniversaryService.deleteAnniversary(userId, id);
    }
}