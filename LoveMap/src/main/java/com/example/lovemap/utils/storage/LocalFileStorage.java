package com.example.lovemap.utils.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件系统存储实现。
 * <p>
 * 通过 {@code file.storage=local} 启用。
 * <p>
 * 文件写入 {@code file.local.base-dir}（默认 {@code ${user.dir}/uploads}），
 * 启动时自动建目录；URL 形如 {@code http://localhost:8080/uploads/...}，
 * 由 nginx 或 Spring 静态资源映射对外暴露。
 *
 * @author LoveMap
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "file.storage", havingValue = "local")
public class LocalFileStorage implements FileStorage {

    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    ));
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"
    ));
    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024L;
    private static final long MAX_PHOTO_SIZE  = 20 * 1024 * 1024L;

    @Value("${file.local.base-dir:${user.dir}/uploads}")
    private String baseDir;

    @Value("${file.local.url-prefix:http://localhost:8080/uploads}")
    private String urlPrefix;

    @Value("${aliyun.oss.avatar_path:LoveMap/avatar/}")
    private String avatarPath;

    @Value("${aliyun.oss.photo_path:LoveMap/photo/}")
    private String photoPath;

    @PostConstruct
    public void init() {
        try {
            Path dir = Paths.get(baseDir).toAbsolutePath();
            Files.createDirectories(dir);
            // 预建子目录（避免首次上传时延迟）
            Files.createDirectories(dir.resolve(avatarPath));
            Files.createDirectories(dir.resolve(photoPath));
            log.info("[FileStorage-Local] 已初始化本地存储目录: {}, URL 前缀: {}", dir, urlPrefix);
        } catch (IOException e) {
            log.error("[FileStorage-Local] 初始化本地存储目录失败: {}", e.getMessage(), e);
            throw new IllegalStateException("本地存储目录初始化失败: " + baseDir, e);
        }
    }

    // ================ FileStorage ================

    @Override
    public String uploadAvatar(MultipartFile file, Integer userId) throws IOException {
        validate(file, MAX_AVATAR_SIZE);
        String ext = extOf(file.getOriginalFilename());
        if (ext.isEmpty()) ext = "jpg";
        String key = avatarPath + "avatar/" + uniqueName(userId, ext);
        return uploadFile(file, key);
    }

    @Override
    public String uploadPhoto(MultipartFile file, Integer userId) throws IOException {
        validate(file, MAX_PHOTO_SIZE);
        String ext = extOf(file.getOriginalFilename());
        if (ext.isEmpty()) ext = "jpg";
        String key = photoPath + uniqueName(userId, ext);
        return uploadFile(file, key);
    }

    @Override
    public String uploadBytes(byte[] bytes, String objectKey, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("字节流不能为空");
        }
        try {
            Path target = resolve(objectKey);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            log.info("[FileStorage-Local] bytes 上传成功, key={}, size={}B", objectKey, bytes.length);
            return toUrl(objectKey);
        } catch (IOException e) {
            throw new RuntimeException("本地文件写入失败: " + objectKey, e);
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String objectKey) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        file.transferTo(target.toFile());
        log.info("[FileStorage-Local] 文件上传成功, key={}, size={}B", objectKey, file.getSize());
        return toUrl(objectKey);
    }

    @Override
    public void delete(String objectKey) {
        try {
            Path target = resolve(objectKey);
            boolean deleted = Files.deleteIfExists(target);
            log.info("[FileStorage-Local] 删除 {} {}", deleted ? "成功" : "(不存在)", objectKey);
        } catch (IOException e) {
            log.warn("[FileStorage-Local] 删除失败 {}: {}", objectKey, e.getMessage());
        }
    }

    // ================ 内部工具 ================

    private void validate(MultipartFile file, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("文件大小不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_IMAGE_TYPES.contains(ct.toLowerCase())) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、GIF、WEBP 格式的图片");
        }
        String ext = extOf(file.getOriginalFilename());
        if (ext.isEmpty() || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("文件扩展名不合法");
        }
    }

    private Path resolve(String objectKey) {
        // 防穿越：objectKey 不能含 ".." 或绝对路径前缀
        if (objectKey.contains("..") || Paths.get(objectKey).isAbsolute()) {
            throw new IllegalArgumentException("非法的 objectKey: " + objectKey);
        }
        return Paths.get(baseDir).toAbsolutePath().resolve(objectKey).normalize();
    }

    private String toUrl(String objectKey) {
        // 把 Windows 反斜杠统一换成 URL 正斜杠
        String normalized = objectKey.replace('\\', '/');
        String prefix = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
        return prefix + normalized;
    }

    private String uniqueName(Integer userId, String ext) {
        return String.format("%d_%s.%s", userId,
                UUID.randomUUID().toString().replace("-", ""), ext);
    }

    private String extOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
