package com.example.lovemap.config;

import com.example.lovemap.mapper.ExportMapper;
import com.example.lovemap.model.entity.ExportRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 导出文件清理定时任务
 * <p>
 * 定期清理导出目录中超过 1 小时的 ZIP/PDF 文件及对应的数据库记录，
 * 同时清理可能遗留的临时下载目录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportCleanupTask {

    private final ExportMapper exportMapper;

    @Value("${export.storage-path:${java.io.tmpdir}/loveofus/exports}")
    private String exportStoragePath;

    /**
     * 每小时执行一次清理，清理超过 1 小时的过期导出文件
     */
    @Scheduled(fixedRate = 3600_000)
    public void cleanupExpiredExports() {
        LocalDateTime deadline = LocalDateTime.now().minus(1, ChronoUnit.HOURS);

        // 1. 从数据库查询已完成的过期导出记录
        List<ExportRecord> expired = exportMapper.findExpiredBefore(deadline);
        if (!expired.isEmpty()) {
            log.info("找到 {} 条过期导出记录，开始清理", expired.size());
            for (ExportRecord record : expired) {
                try {
                    // 删除导出的 ZIP/PDF 文件
                    if (record.getFilePath() != null) {
                        Path filePath = Paths.get(record.getFilePath());
                        Files.deleteIfExists(filePath);
                    }
                    // 删除数据库记录
                    exportMapper.deleteById(record.getId());
                    log.debug("清理过期导出: id={}, path={}", record.getId(), record.getFilePath());
                } catch (IOException e) {
                    log.warn("清理导出文件失败: id={}, path={}", record.getId(), record.getFilePath(), e);
                }
            }
        }

        // 2. 扫描文件系统，清理遗留的临时下载目录 (download_*)
        //    这些目录在 ZIP 正常生成后会被删除，但若进程崩溃可能遗留
        Path exportDir = Paths.get(exportStoragePath);
        if (Files.exists(exportDir)) {
            try (Stream<Path> dirs = Files.list(exportDir)) {
                dirs.filter(Files::isDirectory)
                        .filter(dir -> dir.getFileName().toString().startsWith("download_"))
                        .forEach(dir -> {
                            try {
                                // 检查目录的 lastModified 是否超过 1 小时
                                long lastModified = dir.toFile().lastModified();
                                long oneHourAgo = System.currentTimeMillis() - 3600_000;
                                if (lastModified < oneHourAgo) {
                                    deleteDirectory(dir);
                                    log.debug("清理遗留的临时目录: {}", dir);
                                }
                            } catch (Exception e) {
                                log.warn("清理临时目录失败: {}", dir, e);
                            }
                        });
            } catch (IOException e) {
                log.warn("扫描导出目录失败: {}", exportDir, e);
            }
        }

        // 3. 清理文件系统中没有对应数据库记录的孤立 ZIP/PDF 文件
        //    （例如导出记录已被删除但文件残留）
        if (Files.exists(exportDir)) {
            try (Stream<Path> userDirs = Files.list(exportDir)) {
                userDirs.filter(Files::isDirectory)
                        .forEach(userDir -> {
                            try (Stream<Path> files = Files.list(userDir)) {
                                files.filter(Files::isRegularFile)
                                        .filter(f -> {
                                            String name = f.getFileName().toString();
                                            return name.endsWith(".zip") || name.endsWith(".pdf");
                                        })
                                        .forEach(file -> {
                                            try {
                                                long lastModified = file.toFile().lastModified();
                                                long oneHourAgo = System.currentTimeMillis() - 3600_000;
                                                if (lastModified < oneHourAgo) {
                                                    Files.deleteIfExists(file);
                                                    log.debug("清理孤立导出文件: {}", file);
                                                }
                                            } catch (IOException e) {
                                                log.warn("清理孤立文件失败: {}", file, e);
                                            }
                                        });
                            } catch (IOException e) {
                                log.warn("扫描用户导出目录失败: {}", userDir, e);
                            }
                        });
            } catch (IOException e) {
                log.warn("扫描导出根目录失败: {}", exportDir, e);
            }
        }
    }

    /**
     * 递归删除目录及其所有内容
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}