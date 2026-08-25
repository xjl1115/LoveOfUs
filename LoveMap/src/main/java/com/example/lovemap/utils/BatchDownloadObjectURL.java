package com.example.lovemap.utils;

import com.aliyun.sdk.service.oss2.OSSAsyncClient;
import com.aliyun.sdk.service.oss2.OSSAsyncClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OSS 对象批量下载工具
 * 支持从 OSS URL 批量下载文件到本地目录，与项目现有 OSS SDK v2（OSSAsyncClient）保持一致
 */
@Slf4j
@Component
public class BatchDownloadObjectURL {

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * 批量下载 OSS 文件
     *
     * @param urls              OSS 文件 URL 列表
     * @param localDownloadPath 本地下载目录
     * @return 成功下载的文件数量
     */
    public int batchDownload(List<String> urls, String localDownloadPath) {
        Objects.requireNonNull(urls, "URL列表不能为空");
        Objects.requireNonNull(localDownloadPath, "下载目录不能为空");

        // 清洗 URL：去除反引号包裹
        List<String> cleanedUrls = urls.stream()
                .map(url -> url != null ? url.replaceAll("^`|`$", "").trim() : "")
                .filter(url -> !url.isEmpty())
                .collect(java.util.stream.Collectors.toList());

        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        int successCount = 0;

        // 按 endpoint 分组，减少 OSS 客户端创建次数
        for (List<String> group : groupByEndpoint(cleanedUrls)) {
            if (group.isEmpty()) continue;

            OSSUrlInfo firstInfo = parseOSSUrl(group.get(0));
            try (OSSAsyncClient client = buildClient(firstInfo.endpoint, firstInfo.region, provider)) {
                for (String url : group) {
                    try {
                        OSSUrlInfo info = parseOSSUrl(url);
                        Path targetPath = resolveTargetPath(localDownloadPath, info.objectKey);

                        // 跳过已存在的文件
                        if (Files.exists(targetPath)) {
                            log.info("文件已存在，跳过下载: {}", targetPath);
                            successCount++;
                            continue;
                        }

                        downloadSingleFile(client, info.bucketName, info.objectKey, targetPath);
                        log.info("下载成功: {} -> {}", url, targetPath);
                        successCount++;

                    } catch (Exception e) {
                        log.error("下载失败: {}, 错误: {}", url, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("创建OSS客户端失败", e);
            }
        }

        log.info("批量下载完成: 成功 {}/{} 个文件", successCount, urls.size());
        return successCount;
    }

    /**
     * 下载单个 OSS 文件到指定路径
     *
     * @param client     OSS 客户端
     * @param bucketName 桶名
     * @param objectKey  对象键
     * @param targetPath 目标文件路径
     */
    private void downloadSingleFile(OSSAsyncClient client, String bucketName, String objectKey, Path targetPath) throws Exception {
        // 确保父目录存在
        Files.createDirectories(targetPath.getParent());

        // 使用临时文件，防止下载中断留下残损文件
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

        try {
            GetObjectRequest request = GetObjectRequest.newBuilder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            CompletableFuture<GetObjectResult> future = client.getObjectAsync(request);
            GetObjectResult result = future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            try (InputStream inputStream = result.body();
                 OutputStream outputStream = Files.newOutputStream(tempPath)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
            }

            // 下载完成后再重命名，确保文件完整性
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE);

        } catch (Exception e) {
            // 清理临时文件
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
                // 忽略清理失败
            }
            throw e;
        }
    }

    /**
     * 按 endpoint 对 URL 分组，复用 OSS 客户端
     */
    private List<List<String>> groupByEndpoint(List<String> urls) {
        return urls.stream()
                .collect(java.util.stream.Collectors.groupingBy(url -> {
                    try {
                        return parseOSSUrl(url).endpoint;
                    } catch (Exception e) {
                        log.warn("URL解析失败，归入默认分组: {}", url);
                        return "__invalid__";
                    }
                }))
                .values()
                .stream()
                .toList();
    }

    /**
     * 构建 OSS 异步客户端（与项目 PutObjectAsyncUtils 保持一致的创建方式）
     */
    private OSSAsyncClient buildClient(String endpoint, String region, CredentialsProvider provider) {
        OSSAsyncClientBuilder builder = OSSAsyncClient.newBuilder()
                .region(region)
                .credentialsProvider(provider);
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpoint(endpoint);
        }
        return builder.build();
    }

    /**
     * 将 OSS objectKey 解析为本地文件路径
     */
    private Path resolveTargetPath(String baseDir, String objectKey) {
        // 将 OSS 对象路径中的 "/" 转为系统文件分隔符
        String relativePath = objectKey.replace("/", File.separator);
        return Paths.get(baseDir, relativePath);
    }

    /**
     * 解析 OSS URL，提取 bucketName、objectKey、region 和 endpoint
     */
    private OSSUrlInfo parseOSSUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost(); // allenxjl.oss-cn-beijing.aliyuncs.com
            String[] parts = host.split("\\.");

            if (parts.length < 4 || !parts[1].startsWith("oss-")) {
                throw new IllegalArgumentException("不是有效的OSS URL: " + url);
            }

            String bucketName = parts[0];
            String region = parts[1].substring(4); // 从 "oss-cn-beijing" 提取 "cn-beijing"
            String endpoint = "https://" + parts[1] + ".aliyuncs.com"; // https://oss-cn-beijing.aliyuncs.com

            String objectKey = uri.getPath();
            if (objectKey.startsWith("/")) {
                objectKey = objectKey.substring(1);
            }

            return new OSSUrlInfo(bucketName, objectKey, region, endpoint);

        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL格式错误: " + url, e);
        }
    }

    /**
     * OSS URL 解析信息
     */
    private record OSSUrlInfo(String bucketName, String objectKey, String region, String endpoint) {
    }
}