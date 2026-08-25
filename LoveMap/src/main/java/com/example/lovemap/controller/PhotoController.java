package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.PhotoUploadDTO;
import com.example.lovemap.model.vo.PhotoDetailVO;
import com.example.lovemap.model.vo.PhotoUploadVO;
import com.example.lovemap.model.vo.TimelineResultVO;
import com.example.lovemap.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 照片 Controller
 */
@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
@Tag(name = "照片管理")
public class PhotoController {

    private final PhotoService photoService;

    @PostMapping("/upload")
    @Operation(summary = "上传照片（支持批量）")
    public Result<PhotoUploadVO> uploadPhotos(
            @RequestAttribute("userId") Integer userId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("takenDate") String takenDate,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "locationName", required = false) String locationName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "albumId", required = false) Long albumId) {
        PhotoUploadDTO dto = new PhotoUploadDTO();
        dto.setTakenDate(takenDate);
        dto.setCountry(country);
        dto.setProvince(province);
        dto.setCity(city);
        dto.setLocationName(locationName);
        dto.setDescription(description);
        dto.setAlbumId(albumId);
        return photoService.uploadPhotos(userId, files, dto);
    }

    @GetMapping("/timeline")
    @Operation(summary = "获取时间线照片（按月分组）")
    public Result<TimelineResultVO> getTimeline(
            @RequestAttribute("userId") Integer userId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return photoService.getTimeline(userId, page, size, province, city, startDate, endDate);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除照片（从OSS和数据库中物理删除）")
    public Result<Void> deletePhoto(@RequestAttribute("userId") Integer userId,
                                    @PathVariable("id") Long id) {
        return photoService.deletePhoto(userId, id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取照片详情")
    public Result<PhotoDetailVO> getPhotoDetail(@RequestAttribute("userId") Integer userId,
                                                @PathVariable("id") Long id) {
        return photoService.getPhotoDetail(userId, id);
    }
}
