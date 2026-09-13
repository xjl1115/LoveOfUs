package com.example.lovemap.utils.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件存储抽象接口。
 * <p>
 * 通过 {@code file.storage} 配置切换具体实现：
 * <ul>
 *     <li>{@code oss}    —— 阿里云 OSS（生产环境）</li>
 *     <li>{@code local}  —— 本地文件存储（开发/调试环境，无需 OSS 凭证）</li>
 * </ul>
 * <p>
 * 所有上传/删除接口返回值语义统一：返回可直接被前端 {@code <img src>} 访问的完整 URL。
 *
 * @author LoveMap
 */
public interface FileStorage {

    /**
     * 上传头像
     *
     * @param file   头像文件
     * @param userId 用户 ID
     * @return 头像访问 URL
     */
    String uploadAvatar(MultipartFile file, Integer userId) throws IOException;

    /**
     * 上传照片
     */
    String uploadPhoto(MultipartFile file, Integer userId) throws IOException;

    /**
     * 上传字节流（用于 AI 生成图等无 MultipartFile 场景）。
     *
     * @param bytes       字节流
     * @param objectKey   相对路径，如 {@code LoveMap/makeover/after/9/202608/xxx.png}
     * @param contentType MIME，如 {@code image/png}
     * @return 访问 URL
     */
    String uploadBytes(byte[] bytes, String objectKey, String contentType);

    /**
     * 上传 MultipartFile 到指定相对路径。
     */
    String uploadFile(MultipartFile file, String objectKey) throws IOException;

    /**
     * 删除文件（按相对路径 / objectKey）
     */
    void delete(String objectKey);
}
