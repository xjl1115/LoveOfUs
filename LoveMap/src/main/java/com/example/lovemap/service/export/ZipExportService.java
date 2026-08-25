package com.example.lovemap.service.export;

import com.example.lovemap.mapper.ProvenceMapper;
import com.example.lovemap.model.dto.ExportDTO;
import com.example.lovemap.model.entity.Photo;
import com.example.lovemap.utils.BatchDownloadObjectURL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP 导出服务
 * 负责将照片批量下载并打包为 ZIP 压缩包
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZipExportService {

    private final BatchDownloadObjectURL batchDownloader;
    private final ProvenceMapper provenceMapper;

    /**
     * 生成 ZIP 压缩包
     *
     * @param taskId            导出任务 ID
     * @param userId            用户 ID
     * @param photos            照片列表
     * @param dto               导出选项
     * @param exportStoragePath 导出存储根目录
     * @return ZIP 文件绝对路径
     */
    public String generateZip(Long taskId, Integer userId, List<Photo> photos, ExportDTO dto,
                              String exportStoragePath) throws IOException {
        // 1. 准备临时目录
        Path exportDir = Paths.get(exportStoragePath, String.valueOf(userId));
        Files.createDirectories(exportDir);

        Path downloadDir = exportDir.resolve("download_" + taskId);
        Files.createDirectories(downloadDir);

        // 2. 构建 OSS URL 列表
        List<String> ossUrls = photos.stream()
                .map(Photo::getStoragePath)
                .filter(path -> path != null && !path.isEmpty())
                .collect(Collectors.toList());

        // 3. 批量下载照片
        log.info("ZIP导出: 开始批量下载照片, taskId={}, count={}", taskId, ossUrls.size());
        int downloaded = batchDownloader.batchDownload(ossUrls, downloadDir.toString());
        log.info("ZIP导出: 批量下载完成, taskId={}, downloaded={}/{}", taskId, downloaded, ossUrls.size());

        // 4. 生成 ZIP 文件
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path zipPath = exportDir.resolve("photos_" + timestamp + "_" + taskId + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            List<File> allFiles = new java.util.ArrayList<>();
            collectFilesRecursively(downloadDir.toFile(), allFiles);

            for (File file : allFiles) {
                if (!file.isFile()) continue;

                String entryName = buildZipEntryName(downloadDir, file, photos, dto);

                ZipEntry entry = new ZipEntry(entryName);
                entry.setSize(file.length());
                entry.setTime(file.lastModified());
                zos.putNextEntry(entry);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }

            // 5. 可选的元数据
            if (Boolean.TRUE.equals(dto.getIncludeMetadata())) {
                for (Photo photo : photos) {
                    String metaEntryName = "metadata/" + photo.getId() + ".txt";
                    ZipEntry metaEntry = new ZipEntry(metaEntryName);
                    zos.putNextEntry(metaEntry);
                    zos.write(buildMetadata(photo).getBytes("UTF-8"));
                    zos.closeEntry();
                }
            }
        }

        // 6. 清理下载的临时文件
        deleteDirectory(downloadDir);

        return zipPath.toAbsolutePath().toString();
    }

    /**
     * 解析分组维度：兼容旧字段 groupByDate，默认按拍摄日期分组
     * 取值：none / takenDate / createdAt
     */
    private String resolveGroupBy(ExportDTO dto) {
        if (dto.getGroupBy() != null && !dto.getGroupBy().isEmpty()) {
            return dto.getGroupBy();
        }
        return Boolean.TRUE.equals(dto.getGroupByDate()) ? "takenDate" : "none";
    }

    /**
     * 根据分组维度取出该照片对应的日期文件夹名（年/月/日 三级目录）
     */
    private String resolveDateFolder(Photo photo, String groupBy) {
        switch (groupBy) {
            case "takenDate":
                return photo.getTakenDate() != null
                        ? photo.getTakenDate().toString().replace("-", "/")
                        : "unknown_date";
            case "createdAt":
                if (photo.getCreatedAt() == null) {
                    return "unknown_time";
                }
                return photo.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            default:
                return "unknown_date";
        }
    }

    /**
     * 取出照片对应的省份文件夹名（已查 ProvenceMapper，无省份时落"未知省份"）
     */
    private String resolveProvinceFolder(Photo photo) {
        if (photo.getProvince() == null) {
            return "未知省份";
        }
        String name = getProvinceNameById(photo.getProvince());
        // 防御：省份名称若包含路径分隔符，强制替换为安全字符
        if (name == null || name.isEmpty()) {
            return "未知省份";
        }
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    /**
     * 从 OSS 下载文件名（如 "10_ef8de8ffeb6f43d48c5b0ed50d9b62d8.jpg"）提取纯扩展名
     */
    private String extractExtension(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        return dot >= 0 ? originalName.substring(dot) : "";
    }

    /**
     * 为文件构造人类可读的输出文件名：yyyyMMdd_<photoId>.<ext>
     * 避免暴露 OSS 中的 hash 命名，且同一日多张照片通过 photoId 保证唯一
     */
    private String buildOutputFileName(Photo photo, String originalName) {
        String ext = extractExtension(originalName);
        String datePart = "unknown";
        if (photo.getTakenDate() != null) {
            datePart = photo.getTakenDate().toString().replace("-", "");
        } else if (photo.getCreatedAt() != null) {
            datePart = photo.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        return datePart + "_" + photo.getId() + ext;
    }

    /**
     * 构建 ZIP 条目名称：
     * 目录层级为 "年/月/日/省份/<yyyyMMdd>_<photoId>.<ext>"
     * - 不再保留 OSS 原始子目录路径（如 LoveMap/photo/）
     * - 文件名剥离 hash，使用 photoId 保证唯一
     * - 日期来自 groupBy 维度，省份来自 ProvenceMapper
     */
    private String buildZipEntryName(Path downloadDir, File file, List<Photo> photos, ExportDTO dto) {
        String groupBy = resolveGroupBy(dto);
        String originalName = file.getName();

        // 在 photos 列表中匹配本文件对应的 Photo 记录
        Photo matched = null;
        for (Photo photo : photos) {
            if (photo.getStoragePath() != null && photo.getStoragePath().contains(originalName)) {
                matched = photo;
                break;
            }
        }

        if (matched == null) {
            // 找不到对应 Photo 记录，保守使用"未知日期/未知省份"
            return "unknown_date/未知省份/" + originalName;
        }

        // "none" 分组也按拍摄日期组织目录，方便用户按时间翻看
        String dateFolder = resolveDateFolder(matched, "none".equals(groupBy) ? "takenDate" : groupBy);
        String provinceFolder = resolveProvinceFolder(matched);
        String outName = buildOutputFileName(matched, originalName);
        return dateFolder + "/" + provinceFolder + "/" + outName;
    }

    /**
     * 递归收集目录下所有文件
     */
    private void collectFilesRecursively(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectFilesRecursively(file, result);
            } else {
                result.add(file);
            }
        }
    }

    /**
     * 构建照片元数据文本
     */
    private String buildMetadata(Photo photo) {
        // 查询省份名称
        String provinceName = "";
        if (photo.getProvince() != null) {
            // 通过缓存或数据库查询省份名称
            provinceName = getProvinceNameById(photo.getProvince());
        }
        return String.format(
                "照片ID: %d\n拍摄日期: %s\n地点: %s\n城市: %s\n省份: %s\n国家: %s\n描述: %s\n上传时间: %s",
                photo.getId(),
                photo.getTakenDate() != null ? photo.getTakenDate().toString() : "",
                photo.getLocationName() != null ? photo.getLocationName() : "",
                photo.getCity() != null ? photo.getCity() : "",
                provinceName,
                photo.getCountry() != null ? photo.getCountry() : "",
                photo.getDescription() != null ? photo.getDescription() : "",
                photo.getCreatedAt() != null ? photo.getCreatedAt().toString() : ""
        );
    }

    /**
     * 根据省份ID查询省份名称
     */
    private String getProvinceNameById(Integer provinceId) {
        if (provinceId == null) {
            return "";
        }
        // 简单实现：直接查询数据库
        // 如需性能优化，可添加本地缓存
        String name = provenceMapper.selectNameById(provinceId);
        return name != null ? name : String.valueOf(provinceId);
    }

    /**
     * 递归删除目录及其所有内容
     */
    static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}