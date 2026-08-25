package com.example.lovemap.utils;

import com.example.lovemap.model.dto.ExportDTO;
import com.example.lovemap.model.entity.Photo;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 相册生成器
 * 使用 iText 7 将照片排版为可打印的 PDF 相册
 */
@Slf4j
public class PdfAlbumGenerator {

    /** 内置中文字体路径（classpath:fonts/） */
    private static final String FONT_PATH = "fonts/NotoSansSC-Regular.ttf";

    private PdfFont chineseFont;

    /**
     * 生成 PDF 相册
     *
     * @param photos        照片列表
     * @param dto           导出选项（含 photosPerPage, coverStyle, includeDescription）
     * @param albumTitle    相册标题
     * @param outputDir     输出目录
     * @param taskId        任务ID（用于文件名）
     * @param localImageDir 已下载到本地的照片目录；传入 null 时回退到从 storagePath 直连 OSS
     * @return PDF 文件路径
     */
    public String generatePdf(List<Photo> photos, ExportDTO dto, String albumTitle,
                               String outputDir, Long taskId, String localImageDir) throws IOException {
        // 1. 加载中文字体
        chineseFont = loadChineseFont();

        // 2. 创建输出文件
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path pdfPath = Paths.get(outputDir, "album_" + timestamp + "_" + taskId + ".pdf");
        Files.createDirectories(pdfPath.getParent());

        // 3. 初始化 PDF 文档
        PdfWriter writer = new PdfWriter(new FileOutputStream(pdfPath.toFile()));
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.A4);
        document.setMargins(30, 30, 30, 30);

        try {
            // 4. 生成封面
            generateCover(document, albumTitle, dto);

            // 5. 计算每页照片数
            int photosPerPage = dto.getPhotosPerPage() != null ? dto.getPhotosPerPage() : 4;
            if (photosPerPage != 1 && photosPerPage != 2 && photosPerPage != 4 && photosPerPage != 6 && photosPerPage != 9) {
                photosPerPage = 4;
            }

            // 6. 按页添加照片
            List<List<Photo>> pages = partitionPhotos(photos, photosPerPage);
            for (List<Photo> pagePhotos : pages) {
                generatePhotoPage(document, pagePhotos, photosPerPage, dto, localImageDir);
            }

        } finally {
            // 7. 关闭文档
            document.close();
        }

        log.info("PDF 相册生成完成: {}, 共 {} 页 {} 张照片", pdfPath, (int) Math.ceil((double) photos.size() / getEffectivePageSize(dto)), photos.size());
        return pdfPath.toAbsolutePath().toString();
    }

    /**
     * 生成封面页
     */
    private void generateCover(Document document, String title, ExportDTO dto) {
        boolean isRomantic = "romantic".equals(dto.getCoverStyle());

        // 上方留白
        document.add(new Paragraph("\n\n\n\n\n"));

        // 主标题
        Paragraph titlePara = new Paragraph(title)
                .setFont(chineseFont)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(36)
                .setBold()
                .setFontColor(isRomantic ? new DeviceRgb(220, 80, 120) : ColorConstants.BLACK);
        document.add(titlePara);

        if (isRomantic) {
            document.add(new Paragraph("\n"));
            // 浪漫副标题
            Paragraph subTitle = new Paragraph("Our Love Map · 我们的LOVE地图")
                    .setFont(chineseFont)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setFontColor(new DeviceRgb(255, 150, 180));
            document.add(subTitle);
        }

        // 分隔线
        document.add(new Paragraph("\n"));
        Paragraph line = new Paragraph("— — — — — — — — — —")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(14)
                .setFontColor(ColorConstants.LIGHT_GRAY);
        document.add(line);

        // 日期信息
        document.add(new Paragraph("\n"));
        String dateInfo = "导出日期: " + LocalDate.now().toString();
        if (dto.getStartDate() != null && dto.getEndDate() != null) {
            dateInfo += "  ·  照片范围: " + dto.getStartDate() + " ~ " + dto.getEndDate();
        }
        Paragraph datePara = new Paragraph(dateInfo)
                .setFont(chineseFont)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(11)
                .setFontColor(ColorConstants.GRAY);
        document.add(datePara);

        // 分页
        document.add(new com.itextpdf.layout.element.AreaBreak());
    }

    /**
     * 生成照片页
     */
    private void generatePhotoPage(Document document, List<Photo> photos, int photosPerPage,
                                    ExportDTO dto, String localImageDir) throws IOException {
        int cols = calculateColumns(photosPerPage);
        int rows = (int) Math.ceil((double) photos.size() / cols);

        Table table = new Table(cols);
        table.setWidth(UnitValue.createPercentValue(100));
        table.setVerticalAlignment(VerticalAlignment.MIDDLE);

        for (Photo photo : photos) {
            Cell cell = new Cell();
            cell.setBorder(Border.NO_BORDER);
            cell.setPadding(6);

            // 解析本地图片路径：优先使用已下载的本地文件，回退到 OSS URL
            String imageSource = resolveImageSource(photo, localImageDir);

            if (imageSource != null) {
                try {
                    Image pdfImage = new Image(ImageDataFactory.create(imageSource));
                    pdfImage.setAutoScale(true);
                    pdfImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    cell.add(pdfImage);
                } catch (Exception e) {
                    log.warn("加载照片失败: id={}, source={}", photo.getId(), imageSource, e);
                    cell.add(new Paragraph("[图片加载失败]")
                            .setFont(chineseFont)
                            .setFontColor(ColorConstants.RED)
                            .setTextAlignment(TextAlignment.CENTER));
                }
            } else {
                cell.add(new Paragraph("[图片缺失]")
                        .setFont(chineseFont)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER));
            }

            // 添加描述文字
            if (Boolean.TRUE.equals(dto.getIncludeDescription()) && photo.getDescription() != null
                    && !photo.getDescription().isEmpty()) {
                StringBuilder descText = new StringBuilder();
                descText.append(photo.getDescription());

                if (photo.getTakenDate() != null) {
                    descText.append("\n").append(photo.getTakenDate().toString());
                }
                if (photo.getLocationName() != null && !photo.getLocationName().isEmpty()) {
                    descText.append("  ·  ").append(photo.getLocationName());
                }

                Paragraph desc = new Paragraph(descText.toString())
                        .setFont(chineseFont)
                        .setFontSize(8)
                        .setFontColor(ColorConstants.DARK_GRAY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(4);
                cell.add(desc);
            }

            table.addCell(cell);
        }

        // 填充剩余空白单元格
        int totalCells = cols * rows;
        for (int i = photos.size(); i < totalCells; i++) {
            Cell emptyCell = new Cell();
            emptyCell.setBorder(Border.NO_BORDER);
            table.addCell(emptyCell);
        }

        document.add(table);
        document.add(new com.itextpdf.layout.element.AreaBreak()); // 新页面
    }

    /**
     * 计算列数
     */
    private int calculateColumns(int photosPerPage) {
        switch (photosPerPage) {
            case 1: return 1;
            case 2: return 1; // 1 列 2 行
            case 4: return 2;
            case 6: return 3;
            case 9: return 3;
            default: return 2;
        }
    }

    /**
     * 解析图片数据源：
     * 1. 若提供了 localImageDir，则根据 OSS objectKey 在本地拼出绝对路径，文件存在则用本地
     * 2. 否则回退到原始 OSS URL
     * 3. 都不存在时返回 null，调用方走"[图片缺失]"占位
     */
    private String resolveImageSource(Photo photo, String localImageDir) {
        String storagePath = photo.getStoragePath();
        if (storagePath == null || storagePath.isEmpty()) {
            return null;
        }
        // 清洗 URL：去掉反引号与首尾空白
        String cleaned = storagePath.replaceAll("^`|`$", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        // 优先尝试本地下载的文件
        if (localImageDir != null && !localImageDir.isEmpty()) {
            String objectKey = extractObjectKey(cleaned);
            if (objectKey != null && !objectKey.isEmpty()) {
                java.io.File localFile = Paths.get(localImageDir,
                        objectKey.replace("/", java.io.File.separator)).toFile();
                if (localFile.exists() && localFile.length() > 0) {
                    return localFile.getAbsolutePath();
                }
            }
        }
        // 回退：直接用 OSS URL
        return cleaned;
    }

    /**
     * 从 OSS URL 中提取 objectKey（路径部分）
     */
    private String extractObjectKey(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                return path.substring(1);
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取有效的每页照片数（用于计算页数）
     */
    private int getEffectivePageSize(ExportDTO dto) {
        int p = dto.getPhotosPerPage() != null ? dto.getPhotosPerPage() : 4;
        if (p != 1 && p != 2 && p != 4 && p != 6 && p != 9) return 4;
        return p;
    }

    /**
     * 将照片列表分页
     */
    private List<List<Photo>> partitionPhotos(List<Photo> photos, int pageSize) {
        List<List<Photo>> pages = new ArrayList<>();
        for (int i = 0; i < photos.size(); i += pageSize) {
            int end = Math.min(i + pageSize, photos.size());
            pages.add(photos.subList(i, end));
        }
        return pages;
    }

    /**
     * 加载中文字体
     * 优先加载 classpath 下的 Noto Sans SC 字体，若不存在则回退到 iText 内置的 CJK 字体
     */
    private PdfFont loadChineseFont() {
        // 尝试加载 Noto Sans SC（classpath:fonts/）
        try {
            InputStream fontStream = getClass().getClassLoader().getResourceAsStream(FONT_PATH);
            if (fontStream != null) {
                PdfFont font = PdfFontFactory.createFont(
                        fontStream.readAllBytes(),
                        com.itextpdf.io.font.PdfEncodings.IDENTITY_H);
                log.info("加载中文字体成功: {}", FONT_PATH);
                return font;
            }
        } catch (Exception e) {
            log.warn("加载 Noto Sans SC 失败，回退到内置 CJK 字体: {}", e.getMessage());
        }

        // 回退：使用 iText font-asian 提供的 CJK 字体
        try {
            PdfFont font = PdfFontFactory.createFont(
                    "AdobeSongStd-Light",
                    com.itextpdf.io.font.PdfEncodings.IDENTITY_H);
            log.info("使用内置 CJK 字体: AdobeSongStd-Light");
            return font;
        } catch (IOException e) {
            // 最终回退：系统默认字体
            log.error("加载 CJK 字体也失败，使用系统默认字体", e);
            return null;
        }
    }
}