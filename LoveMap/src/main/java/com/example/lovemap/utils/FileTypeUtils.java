package com.example.lovemap.utils;

import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FileTypeUtils {

    private static final Map<String, String> CONTENT_TYPE_MAP = new HashMap<>();
    
    private static final Set<String> LAW_DOCUMENT_TYPES = Set.of("pdf", "doc", "docx", "md");
    
    private static final Set<String> SYSTEM_DOCUMENT_TYPES = Set.of("docx", "md");
    
    private static final Set<String> CONTRACT_TYPES = Set.of("pdf", "docx");
    
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp");

    static {
        CONTENT_TYPE_MAP.put("pdf", MediaType.APPLICATION_PDF_VALUE);
        CONTENT_TYPE_MAP.put("doc", "application/msword");
        CONTENT_TYPE_MAP.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        CONTENT_TYPE_MAP.put("md", "text/markdown; charset=UTF-8");
        CONTENT_TYPE_MAP.put("txt", "text/plain; charset=UTF-8");
        CONTENT_TYPE_MAP.put("jpg", "image/jpeg");
        CONTENT_TYPE_MAP.put("jpeg", "image/jpeg");
        CONTENT_TYPE_MAP.put("png", "image/png");
        CONTENT_TYPE_MAP.put("gif", "image/gif");
        CONTENT_TYPE_MAP.put("webp", "image/webp");
    }

    public static String getFileExtension(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "";
        }
        
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        
        return filename.substring(lastDotIndex + 1).toLowerCase().trim();
    }

    public static String determineContentType(String fileExtension) {
        if (fileExtension == null || fileExtension.trim().isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        
        String extension = fileExtension.toLowerCase().trim();
        return CONTENT_TYPE_MAP.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    public static boolean isValidLawDocumentType(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        return LAW_DOCUMENT_TYPES.contains(extension.toLowerCase().trim());
    }

    public static boolean isValidSystemDocumentType(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        return SYSTEM_DOCUMENT_TYPES.contains(extension.toLowerCase().trim());
    }

    public static boolean isValidContractType(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        return CONTRACT_TYPES.contains(extension.toLowerCase().trim());
    }

    public static boolean isValidImageType(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        return IMAGE_TYPES.contains(extension.toLowerCase().trim());
    }

    public enum FileType {
        CONTRACT,
        LAW_DOCUMENT,
        SYSTEM_DOCUMENT,
        IMAGE,
        UNKNOWN
    }

    public static FileType getFileType(String filename) {
        String extension = getFileExtension(filename);
        if (extension.isEmpty()) {
            return FileType.UNKNOWN;
        }
        
        if (CONTRACT_TYPES.contains(extension)) {
            return FileType.CONTRACT;
        } else if (LAW_DOCUMENT_TYPES.contains(extension)) {
            return FileType.LAW_DOCUMENT;
        } else if (IMAGE_TYPES.contains(extension)) {
            return FileType.IMAGE;
        } else {
            return FileType.UNKNOWN;
        }
    }
}
