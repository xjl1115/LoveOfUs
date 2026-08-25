package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AlbumCreateDTO;
import com.example.lovemap.model.dto.AlbumUpdateDTO;
import com.example.lovemap.model.vo.AlbumDetailVO;
import com.example.lovemap.model.vo.AlbumVO;
import com.example.lovemap.service.AlbumService;
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

import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@Tag(name = "相册管理", description = "相册管理接口")
@RequestMapping("/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @PostMapping
    @Operation(summary = "创建相册")
    public Result<AlbumVO> createAlbum(@RequestAttribute("userId") Integer userId,
            @Valid @RequestBody AlbumCreateDTO dto) {
        return albumService.createAlbum(userId, dto);
    }

    @GetMapping
    @Operation(summary = "获取相册列表")
    public Result<List<AlbumVO>> listAlbums(@RequestAttribute("userId") Integer userId) {
        return albumService.listAlbums(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取相册详情（含照片列表，分页）")
    public Result<AlbumDetailVO> getAlbumDetail(@RequestAttribute("userId") Integer userId,
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return albumService.getAlbumDetail(userId, id, page, size);
    }

    @GetMapping("/{albumId}/photos")
    @Operation(summary = "获取相册内照片列表（分页）")
    public Result<AlbumDetailVO> getAlbumPhotos(@RequestAttribute("userId") Integer userId,
            @PathVariable("albumId") Long albumId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return albumService.getAlbumDetail(userId, albumId, page, size);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新相册信息")
    public Result<AlbumVO> updateAlbum(@RequestAttribute("userId") Integer userId,
            @PathVariable("id") Long id,
            @RequestBody AlbumUpdateDTO dto) {
        return albumService.updateAlbum(userId, id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除相册")
    public Result<Void> deleteAlbum(@RequestAttribute("userId") Integer userId,
            @PathVariable("id") Long id) {
        return albumService.deleteAlbum(userId, id);
    }

    @PostMapping("/{albumId}/photos")
    @Operation(summary = "批量添加照片到相册")
    public Result<Void> addPhotosToAlbum(@RequestAttribute("userId") Integer userId,
            @PathVariable("albumId") Long albumId,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> photoIds = body.get("photoIds");
        return albumService.addPhotosToAlbum(userId, albumId, photoIds);
    }

    @DeleteMapping("/{albumId}/photos/{photoId}")
    @Operation(summary = "从相册移除照片")
    public Result<Void> removePhotoFromAlbum(@RequestAttribute("userId") Integer userId,
            @PathVariable("albumId") Long albumId,
            @PathVariable("photoId") Long photoId) {
        return albumService.removePhotoFromAlbum(userId, albumId, photoId);
    }
}
