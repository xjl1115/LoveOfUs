package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 聊天附件上传结果 VO
 */
@Data
public class ChatAttachmentVO {

    /** 附件访问 URL */
    private String url;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件 MIME 类型 */
    private String contentType;
}
