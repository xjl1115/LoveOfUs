package com.example.lovemap.utils;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * 图片缩放工具（基于 JDK 自带的 javax.imageio + java.awt，不引入新依赖）
 * <p>
 * 设计目标：把任意尺寸的 JPEG/PNG/WebP 缩放到宽高均在 [minSide, maxSide] 的区间内，保持原比例。
 * <p>
 * 典型场景：DashScope wanx2.1-imageedit 要求 base_image_url 的宽高都在 [512, 4096]，
 * 否则服务端返回 InvalidParameter: The height of the image should be between 512 and 4096 pixels.
 * 这里用于在调 wanx 之前给原图做兜底缩放。
 * <p>
 * 约束：
 *   - 不做 EXIF 旋转（保持原方向，避免与原图视觉不一致）
 *   - 输出格式与输入保持一致（JPEG 仍为 JPEG，PNG 仍为 PNG）
 *   - 已在区间内的图片直接返回原字节，零额外开销
 */
@Slf4j
public final class ImageResizeUtils {

    private ImageResizeUtils() {
    }

    /**
     * 等比缩放图片到目标尺寸区间
     *
     * @param bytes    原图片字节
     * @param minSide  宽/高最小允许值
     * @param maxSide  宽/高最大允许值
     * @return 缩放后的字节；若原图已在区间内则返回原 bytes；读取/写入失败返回原 bytes 并打 warn
     */
    public static byte[] fitIntoRange(byte[] bytes, int minSide, int maxSide) {
        if (bytes == null || bytes.length == 0) return bytes;

        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            log.warn("[ImageResize] 读取图片失败, 跳过缩放: {}", e.getMessage());
            return bytes;
        }
        if (src == null) {
            log.warn("[ImageResize] ImageIO 无法解析该图片, 跳过缩放");
            return bytes;
        }

        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) return bytes;
        log.info("[ImageResize] 输入图片 {}x{}, target=[{},{}]", w, h, minSide, maxSide);
        if (w >= minSide && w <= maxSide && h >= minSide && h <= maxSide) {
            // 已经在合法区间，零开销返回
            log.info("[ImageResize] 原图尺寸已在区间, 透传原图");
            return bytes;
        }

        // 计算缩放比例：按"超长边先压到 maxSide、最短边若 < minSide 再放大到 minSide"两步走
        double scale = 1.0;
        if (w > maxSide || h > maxSide) {
            scale = Math.min((double) maxSide / w, (double) maxSide / h);
        }
        int newW = (int) Math.round(w * scale);
        int newH = (int) Math.round(h * scale);
        if (newW < minSide || newH < minSide) {
            // 极端比例（如超宽横幅）：放大短板到 minSide，避免压到 maxSide 后另一维 < minSide
            double scaleUp = Math.max((double) minSide / newW, (double) minSide / newH);
            newW = (int) Math.round(newW * scaleUp);
            newH = (int) Math.round(newH * scaleUp);
        }
        // 安全护栏
        if (newW > maxSide) newW = maxSide;
        if (newH > maxSide) newH = maxSide;
        if (newW < minSide) newW = minSide;
        if (newH < minSide) newH = minSide;

        BufferedImage dst = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            // PNG 含透明通道：透明像素合成到白底，避免输出 JPEG 时变黑
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int canvasType = src.getType() == BufferedImage.TYPE_INT_ARGB
                    || hasAlpha(src) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            if (canvasType == BufferedImage.TYPE_INT_RGB) {
                g.fillRect(0, 0, newW, newH); // 白底，避免 JPEG 黑边
            }
            g.drawImage(src, 0, 0, newW, newH, null);
        } finally {
            g.dispose();
        }

        // 输出格式与输入一致
        String formatName = detectFormat(bytes);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // PNG 输出带 alpha 时走 TYPE_INT_ARGB
            BufferedImage out = ("png".equals(formatName) && dst.getType() != BufferedImage.TYPE_INT_ARGB)
                    ? toArgb(dst, newW, newH)
                    : dst;
            boolean ok = ImageIO.write(out, formatName, baos);
            if (!ok || baos.size() == 0) {
                log.warn("[ImageResize] 编码失败 format={}, 返回原图", formatName);
                return bytes;
            }
            log.info("[ImageResize] 缩放完成 {}x{} -> {}x{}, format={}, in={}B out={}B",
                    w, h, newW, newH, formatName, bytes.length, baos.size());
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("[ImageResize] 写入失败, 返回原图: {}", e.getMessage());
            return bytes;
        }
    }

    /**
     * 探测图片宽高（不修改字节流），用于打日志/前置校验。
     *
     * @return [width, height]；解析失败返回 null
     */
    public static int[] probe(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return null;
            return new int[]{img.getWidth(), img.getHeight()};
        } catch (IOException e) {
            return null;
        }
    }

    private static String detectFormat(byte[] bytes) {
        // JPEG / PNG / WebP 头判断；其它格式一律回退 JPEG
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        return "jpeg";
    }

    private static boolean hasAlpha(BufferedImage img) {
        return img.getColorModel().hasAlpha();
    }

    private static BufferedImage toArgb(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * 缩放 OSS 上 URL 指向的图片到合法区间，并把缩放结果上传到 OSS 的临时 key。
     * <p>
     * 设计动机：DashScope wanx2.1-imageedit 要求 base_image_url 宽高都在 [512, 4096]，
     * 不合规会返回 InvalidParameter。这里在调 wanx 前下载 → 检查尺寸 → 不合规则缩放 →
     * 上传到 OSS 的临时 key，把临时 URL 传给 wanx；合规则零开销。
     *
     * @param srcUrl       原图 OSS URL
     * @param uploader     OSS 字节流上传工具（注入 PutObjectAsyncUtils）
     * @param objectKey    缩放图目标 OSS key（建议带日期/UUID 避免冲突）
     * @param minSide      最短边下限
     * @param maxSide      最长边上限
     * @return 可直接交给 wanx 的 URL：原图合规 → 原 URL；不合规 → 临时 key 的访问 URL；
     *         缩放失败 → 原 URL（让 wanx 自己报错，至少日志里能看到尺寸不合规）
     */
    public static String ensureWanxCompatible(String srcUrl,
                                             BytesUploader uploader,
                                             String objectKey,
                                             int minSide,
                                             int maxSide) {
        if (srcUrl == null || srcUrl.isBlank()) return srcUrl;
        log.info("[ImageResize] ensureWanxCompatible 开始, srcUrl={}, target=[{},{}]",
                srcUrl, minSide, maxSide);
        byte[] bytes;
        try {
            bytes = download(srcUrl);
            log.info("[ImageResize] 下载原图完成, size={}B", bytes == null ? 0 : bytes.length);
        } catch (IOException e) {
            log.warn("[ImageResize] 下载原图失败, 直接透传原 URL: {}", e.getMessage());
            return srcUrl;
        }
        int[] wh = probe(bytes);
        if (wh != null && wh[0] >= minSide && wh[0] <= maxSide && wh[1] >= minSide && wh[1] <= maxSide) {
            log.info("[ImageResize] 原图尺寸合规 {}x{}, 直接透传 url={}", wh[0], wh[1], srcUrl);
            return srcUrl;
        }
        log.info("[ImageResize] 原图尺寸不合规 src={}x{}, 走缩放 path",
                wh == null ? "?" : wh[0], wh == null ? "?" : wh[1]);
        byte[] resized = fitIntoRange(bytes, minSide, maxSide);
        if (resized == bytes) {
            log.warn("[ImageResize] 缩放未生效, 透传原 URL 交给 wanx（可能仍会 4xx）");
            return srcUrl;
        }
        try {
            String url = uploader.uploadBytes(resized, objectKey, "image/png");
            log.info("[ImageResize] 缩放图已上传 OSS, key={}, url={}", objectKey, url);
            // 防御：上传后再验一次缩放图实际尺寸，避免编码/上传环节意外导致尺寸不合规
            int[] finalWh = probe(resized);
            if (finalWh != null && (finalWh[0] < minSide || finalWh[0] > maxSide
                    || finalWh[1] < minSide || finalWh[1] > maxSide)) {
                log.error("[ImageResize] 上传后实际尺寸仍不合规 {}x{}, 透传原 URL",
                        finalWh[0], finalWh[1]);
                return srcUrl;
            }
            if (finalWh != null) {
                log.info("[ImageResize] 上传后实际尺寸 {}x{}, 已在区间内", finalWh[0], finalWh[1]);
            }
            return url;
        } catch (Exception e) {
            log.warn("[ImageResize] 临时缩放图上传失败, 透传原 URL: {}", e.getMessage(), e);
            return srcUrl;
        }
    }

    private static byte[] download(String url) throws IOException {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        try (java.io.InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }

    private static String guessContentType(byte[] bytes) {
        String fmt = detectFormat(bytes);
        return switch (fmt) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    /**
     * 字节上传回调（用于解耦 ImageResizeUtils 与具体 OSS 客户端，避免循环依赖）
     */
    @FunctionalInterface
    public interface BytesUploader {
        /**
         * @return 可直接访问的 URL
         */
        String uploadBytes(byte[] bytes, String objectKey, String contentType);
    }

    /** 简易工厂：用包名兜底（小工具） */
    public static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
