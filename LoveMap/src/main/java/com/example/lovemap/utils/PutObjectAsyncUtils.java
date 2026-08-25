package com.example.lovemap.utils;

import com.aliyun.sdk.service.oss2.OSSAsyncClient;
import com.aliyun.sdk.service.oss2.OSSAsyncClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.DeleteObjectResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 阿里云OSS文件上传工具类
 * 支持头像、合同文件等异步上传
 */
@Slf4j
@Component
public class PutObjectAsyncUtils {

    /**
     * 允许的图片类型
     */
    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    ));

    /**
     * 允许的文件扩展名
     */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"
    ));

    /**
     * 最大文件大小：5MB
     */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 最大照片文件大小：20MB
     */
    private static final long MAX_PHOTO_FILE_SIZE = 20 * 1024 * 1024;

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.region}")
    private String region;

    @Value("${aliyun.oss.bucket}")
    private String bucket;

    @Value("${aliyun.oss.avatar_path}")
    private String avatarPath;

    @Value("${aliyun.oss.photo_path}")
    private String photoPath;

    /**
     * 上传头像文件
     *
     * @param file 头像文件
     * @param userId 用户ID，用于生成唯一文件名
     * @return 头像访问URL
     * @throws IOException 文件读取异常
     * @throws IllegalArgumentException 文件校验失败
     */
    public String uploadAvatar(MultipartFile file, Integer userId) throws IOException {
        // 1. 参数校验
        validateAvatarFile(file);

        // 2. 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = FileTypeUtils.getFileExtension(originalFilename);
        if (extension.isEmpty()) {
            extension = "unknown";
        }
        String uniqueFileName = generateUniqueFileName(userId, extension);
        String objectKey = avatarPath + "avatar/" + uniqueFileName;

        // 3. 上传文件
        return uploadFile(file, objectKey);
    }

    /**
     * 上传照片文件
     *
     * @param file 照片文件
     * @param userId 用户ID，用于生成唯一文件名
     * @return 照片访问URL
     * @throws IOException 文件读取异常
     */
    public String uploadPhoto(MultipartFile file, Integer userId) throws IOException {
        // 1. 参数校验
        validatePhotoFile(file);

        // 2. 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = FileTypeUtils.getFileExtension(originalFilename);
        if (extension.isEmpty()) {
            extension = "unknown";
        }
        String uniqueFileName = generateUniqueFileName(userId, extension);
        String objectKey = photoPath + uniqueFileName;

        // 3. 上传文件
        return uploadFile(file, objectKey);
    }

    /**
     * 上传文件到OSS
     *
     * @param file 文件
     * @param objectKey OSS对象键
     * @return 文件访问URL
     * @throws IOException 文件读取异常
     */
    public String uploadFile(MultipartFile file, String objectKey) throws IOException {
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();

        try (OSSAsyncClient client = getDefaultAsyncClient(endpoint, region, provider)) {
            byte[] fileBytes = file.getBytes();

            PutObjectResult result = client.putObjectAsync(PutObjectRequest.newBuilder()
                    .bucket(bucket)
                    .key(objectKey)
                    .body(BinaryData.fromBytes(fileBytes))
                    .contentType(file.getContentType())
                    .build()).get();

            log.info("文件上传成功, statusCode: {}, requestId: {}, eTag: {}",
                    result.statusCode(), result.requestId(), result.eTag());

            // 构建文件访问URL
            return buildFileUrl(objectKey);

        } catch (Exception e) {
            handleUploadException(e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 校验头像文件
     *
     * @param file 文件
     * @throws IllegalArgumentException 校验失败时抛出
     */
    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        // 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("头像文件大小不能超过5MB");
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、GIF、WEBP 格式的图片");
        }

        // 校验文件扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = FileTypeUtils.getFileExtension(originalFilename);
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("文件扩展名不合法");
        }
    }

    /**
     * 校验照片文件
     *
     * @param file 文件
     * @throws IllegalArgumentException 校验失败时抛出
     */
    private void validatePhotoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        // 校验文件大小
        if (file.getSize() > MAX_PHOTO_FILE_SIZE) {
            throw new IllegalArgumentException("照片文件大小不能超过20MB");
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、GIF、WEBP 格式的图片");
        }

        // 校验文件扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = FileTypeUtils.getFileExtension(originalFilename);
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("文件扩展名不合法");
        }
    }

    /**
     * 生成唯一文件名
     *
     * @param userId 用户ID
     * @param extension 文件扩展名
     * @return 唯一文件名
     */
    private String generateUniqueFileName(Integer userId, String extension) {
        return String.format("%d_%s.%s", userId, UUID.randomUUID().toString().replace("-", ""), extension);
    }

    /**
     * 构建文件访问URL
     *
     * @param objectKey OSS对象键
     * @return 完整访问URL
     */
    private String buildFileUrl(String objectKey) {
        // 如果配置了自定义域名，使用自定义域名
        // 否则使用OSS默认域名
        return String.format("https://%s.%s/%s", bucket, endpoint.replace("https://", "").replace("http://", ""), objectKey);
    }

    /**
     * 处理上传异常
     *
     * @param e 异常
     */
    private void handleUploadException(Exception e) {
        ServiceException se = ServiceException.asCause(e);
        if (se != null) {
            log.error("OSS服务异常, requestId: {}, errorCode: {}, errorMessage: {}",
                    se.requestId(), se.errorCode(), se.getMessage());
        } else {
            log.error("文件上传异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取OSS客户端
     *
     * @param endpoint OSS端点
     * @param region OSS区域
     * @param provider 凭证提供器
     * @return OSS异步客户端
     */
    private OSSAsyncClient getDefaultAsyncClient(String endpoint, String region, CredentialsProvider provider) {
        OSSAsyncClientBuilder builder = OSSAsyncClient.newBuilder()
                .region(region)
                .credentialsProvider(provider);
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpoint(endpoint);
        }
        return builder.build();
    }

    /**
     * 删除OSS文件
     *
     * @param objectKey OSS对象键
     */
    public void deleteFile(String objectKey) {
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();

        try (OSSAsyncClient client = getDefaultAsyncClient(endpoint, region, provider)) {
            DeleteObjectResult result = client.deleteObjectAsync(DeleteObjectRequest.newBuilder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build()).get();

            log.info("文件删除成功, statusCode: {}, requestId: {}",
                    result.statusCode(), result.requestId());

        } catch (Exception e) {
            handleUploadException(e);
            throw new RuntimeException("文件删除失败", e);
        }
    }
}
