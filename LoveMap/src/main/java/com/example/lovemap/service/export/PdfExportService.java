package com.example.lovemap.service.export;

import com.example.lovemap.model.dto.ExportDTO;
import com.example.lovemap.model.entity.Photo;
import com.example.lovemap.utils.BatchDownloadObjectURL;
import com.example.lovemap.utils.PdfAlbumGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PDF 导出服务
 * 负责将照片排版生成 PDF 相册
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final BatchDownloadObjectURL batchDownloader;

    /**
     * 生成 PDF 相册
     *
     * @param taskId            导出任务 ID
     * @param userId            用户 ID
     * @param photos            照片列表
     * @param dto               导出选项
     * @param exportStoragePath 导出存储根目录
     * @return PDF 文件绝对路径
     */
    public String generatePdf(Long taskId, Integer userId, List<Photo> photos, ExportDTO dto,
                              String exportStoragePath) throws IOException {
        Path exportDir = Paths.get(exportStoragePath, String.valueOf(userId));
        Files.createDirectories(exportDir);

        // 先批量下载到本地，避免 PDF 生成阶段网络抖动导致图片加载失败
        Path downloadDir = exportDir.resolve("download_" + taskId);
        Files.createDirectories(downloadDir);

        List<String> ossUrls = photos.stream()
                .map(Photo::getStoragePath)
                .filter(path -> path != null && !path.isEmpty())
                .collect(Collectors.toList());
        log.info("PDF导出: 开始批量下载照片, taskId={}, count={}", taskId, ossUrls.size());
        int downloaded = batchDownloader.batchDownload(ossUrls, downloadDir.toString());
        log.info("PDF导出: 批量下载完成, taskId={}, downloaded={}/{}", taskId, downloaded, ossUrls.size());

        String albumTitle = "LoveMap - 我们的相册";
        if (dto.getStartDate() != null && dto.getEndDate() != null) {
            albumTitle += " (" + dto.getStartDate() + " ~ " + dto.getEndDate() + ")";
        }

        try {
            PdfAlbumGenerator generator = new PdfAlbumGenerator();
            return generator.generatePdf(photos, dto, albumTitle, exportDir.toString(), taskId,
                    downloadDir.toString());
        } finally {
            // 清理下载的临时文件
            deleteDirectory(downloadDir);
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        }
    }
}