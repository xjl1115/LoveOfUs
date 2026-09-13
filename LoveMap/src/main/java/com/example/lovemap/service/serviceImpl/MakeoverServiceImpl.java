package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.PageResult;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.VipConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.makeover.constant.MakeoverConstant;
import com.example.lovemap.makeover.mapper.MakeoverRecordMapper;
import com.example.lovemap.makeover.task.MakeoverAsyncExecutor;
import com.example.lovemap.model.dto.MakeoverCreateDTO;
import com.example.lovemap.model.entity.MakeoverRecord;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.MakeoverCreateVO;
import com.example.lovemap.model.vo.MakeoverDetailVO;
import com.example.lovemap.model.vo.MakeoverListVO;
import com.example.lovemap.model.vo.MakeoverQuotaVO;
import com.example.lovemap.service.MakeoverService;
import com.example.lovemap.service.RateLimiterService;
import com.example.lovemap.service.VipService;
import com.example.lovemap.utils.ImageResizeUtils;
import com.example.lovemap.utils.storage.FileStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 化妆建议服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MakeoverServiceImpl implements MakeoverService {

    private final MakeoverRecordMapper recordMapper;
    private final MakeoverAsyncExecutor asyncExecutor;
    private final FileStorage fileStorage;
    private final UserMapper userMapper;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final VipService vipService;

    /** 每个账户每月免费 AI 化妆建议次数，VIP 档位在此基础上叠加额外次数 */
    @Value("${makeover.monthly-free-quota:3}")
    private int monthlyFreeQuota;

    @Override
    public MakeoverCreateVO create(Integer userId, MultipartFile image, MakeoverCreateDTO dto) {
        validateScene(dto.getScene());
        validateImage(image);

        // 月度额度校验放在最前：无额度时直接提示升级，不做无谓的上传与 OSS 调用
        MakeoverQuotaVO quota = quota(userId);
        if (!Boolean.TRUE.equals(quota.getUnlimited()) && quota.getRemaining() <= 0) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "本月 AI 化妆建议次数已用完，升级 VIP 可获得更多次数");
        }

        // 用户级限流
        if (!rateLimiterService.checkAndRecordForUser(
                MakeoverConstant.RATE_LIMIT_KEY_PREFIX + userId,
                MakeoverConstant.USER_CONCURRENT_LIMIT, Duration.ofMinutes(1))) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "提交过于频繁，请稍后再试");
        }

        // wanx2.1-imageedit 要求原图宽高都在 [512, 4096]。
        // 不合规则在客户端自动缩放到合法区间后上传到 OSS，避免直接拒绝用户上传导致 UX 割裂。
        MultipartFile uploadImage = image;
        try {
            byte[] raw = image.getBytes();
            int min = MakeoverConstant.WANX_MIN_SIDE_PX;
            int max = MakeoverConstant.WANX_MAX_SIDE_PX;
            int[] wh = ImageResizeUtils.probe(raw);
            if (wh == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "无法识别图片尺寸，请重新上传");
            }
            if (wh[0] < min || wh[0] > max || wh[1] < min || wh[1] > max) {
                log.info("[Makeover] 上传图片尺寸不合规 {}x{}, 自动缩放至 [{}-{}]", wh[0], wh[1], min, max);
                byte[] resized = ImageResizeUtils.fitIntoRange(raw, min, max);
                if (resized == raw) {
                    // 缩放失败
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                            "图片尺寸需在 [" + min + ", " + max + "] 像素之间，当前 " + wh[0] + "x" + wh[1]
                                    + "，且自动缩放失败，请手动裁剪后重新上传");
                }
                uploadImage = new InMemoryMultipartFile(resized,
                        image.getOriginalFilename(), image.getContentType());
                log.info("[Makeover] 自动缩放完成, 上传 {}B -> {}B", raw.length, resized.length);
            }
        } catch (IOException e) {
            log.warn("[Makeover] 读取上传图片失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片读取失败，请重新上传");
        }

        // 上传 OSS
        String objectKey;
        String originalUrl;
        try {
            objectKey = buildOriginKey(userId, uploadImage);
            originalUrl = fileStorage.uploadFile(uploadImage, objectKey);
        } catch (IOException e) {
            log.error("[Makeover] OSS 上传失败", e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "图片上传失败");
        }

        // 落库
        User user = userMapper.selectById(userId);
        Integer partnerId = (user != null && user.getPartnerId() != null)
                ? Integer.valueOf(String.valueOf(user.getPartnerId())) : null;

        MakeoverRecord record = new MakeoverRecord();
        record.setUserId(userId);
        record.setPartnerId(partnerId);
        record.setOriginalUrl(originalUrl);
        record.setOriginalKey(objectKey);
        record.setSceneCode(dto.getScene());
        record.setSceneText(dto.getDescription());
        // suggestions 在 analyze 完成后回填，这里先塞空 JSON 以满足 NOT NULL 约束
        record.setSuggestions("{}");
        record.setStatus(MakeoverConstant.STATUS_PENDING);
        record.setDeleted((byte) 0);
        recordMapper.insert(record);

        Long recordId = record.getId();
        log.info("[Makeover] 创建化妆建议 recordId={}, userId={}, scene={}", recordId, userId, dto.getScene());

        // 投递 @Async
        asyncExecutor.run(recordId);

        MakeoverCreateVO vo = new MakeoverCreateVO();
        vo.setRecordId(recordId);
        vo.setStatus(MakeoverConstant.STATUS_PENDING);
        vo.setOriginalUrl(originalUrl);
        return vo;
    }

    @Override
    public MakeoverQuotaVO quota(Integer userId) {
        // VIP 归属情侣组：额外额度按组内生效等级计算，双方各自独立计数
        User user = userMapper.selectById(userId);
        int vipLevel = vipService.resolveEffectiveVip(user).level();
        Integer total = VipConstant.makeoverMonthlyQuota(vipLevel, monthlyFreeQuota);

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        int used = (int) recordMapper.countMonthlyByUser(userId, monthStart, monthStart.plusMonths(1));

        MakeoverQuotaVO vo = new MakeoverQuotaVO();
        vo.setUsed(used);
        vo.setUnlimited(total == null);
        vo.setTotal(total);
        vo.setRemaining(total == null ? null : Math.max(0, total - used));
        return vo;
    }

    @Override
    public MakeoverDetailVO detail(Integer userId, Long id) {
        MakeoverRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        if (!canAccess(userId, record)) {
            // 跨用户访问视为 404，避免泄露存在性
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }

        MakeoverDetailVO vo = new MakeoverDetailVO();
        vo.setRecordId(record.getId());
        vo.setStatus(record.getStatus());
        vo.setSceneCode(record.getSceneCode());
        vo.setSceneText(record.getSceneText());
        vo.setOriginalUrl(record.getOriginalUrl());
        vo.setAfterUrl(record.getAfterUrl());
        vo.setErrorMessage(record.getErrorMessage());
        vo.setCostMs(record.getCostMs());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setOwnerId(record.getUserId());
        vo.setOwnedByCurrent(record.getUserId().equals(userId));

        // 读穿 Redis：先读 ai-result 缓存（Stage1 写入），未命中回退到 DB 字段
        Map<String, Object> faceMap = readAiResultFaceFeatures(id);
        if (faceMap != null) {
            vo.setFaceFeatures(faceMap);
        } else {
            vo.setFaceFeatures(parseJson(record.getFaceFeatures()));
        }
        Map<String, Object> suggMap = readAiResultSuggestions(id);
        if (suggMap != null) {
            vo.setSuggestions(suggMap);
        } else {
            vo.setSuggestions(parseJson(record.getSuggestions()));
        }
        // summary 字段：先 Redis 后 DB；老记录（summary 列 NULL 或为空）静默跳过
        Map<String, Object> summaryMap = readAiResultSummary(id);
        if (summaryMap != null) {
            vo.setSummary(summaryMap);
        } else if (record.getSummary() != null && !record.getSummary().isBlank()) {
            vo.setSummary(parseJson(record.getSummary()));
        }
        return vo;
    }

    @Override
    public PageResult<MakeoverListVO> list(Integer userId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 50) size = 50;
        long total = recordMapper.countByUser(userId);
        List<MakeoverRecord> records = recordMapper.selectPageByUser(userId,
                (page - 1) * size, size);
        List<MakeoverListVO> list = records.stream().map(this::toListVO).toList();
        return new PageResult<>(list, total, page, size);
    }

    @Override
    public void softDelete(Integer userId, Long id) {
        int n = recordMapper.softDelete(id, userId);
        if (n == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在或已删除");
        }
        // 删除后清理 Redis 缓存（避免脏数据 & 节省内存）
        evictAiResultCache(id);
        log.info("[Makeover] 软删除 recordId={}, userId={}", id, userId);
    }

    @Override
    public void cancel(Integer userId, Long id) {
        MakeoverRecord record = recordMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        if (record.getStatus() != MakeoverConstant.STATUS_PENDING
                && record.getStatus() != MakeoverConstant.STATUS_ANALYZING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不可取消");
        }
        recordMapper.updateStatus(id, MakeoverConstant.STATUS_CANCELED, "用户取消", null);
        log.info("[Makeover] 取消 recordId={}, userId={}", id, userId);
    }

    // ====== 私有辅助 ======

    private void validateScene(String scene) {
        if (scene == null || scene.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择场景");
        }
        if (!MakeoverConstant.SCENE_NAME.containsKey(scene)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "场景编码不合法");
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请上传自拍照");
        }
        if (image.getSize() > MakeoverConstant.MAX_IMAGE_SIZE_BYTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片不能超过 8MB");
        }
        String contentType = image.getContentType();
        if (contentType == null || !MakeoverConstant.ALLOWED_MIME.contains(contentType.toLowerCase())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 jpg/png/webp 格式");
        }
    }

    private String buildOriginKey(Integer userId, MultipartFile file) {
        String yyyymm = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String original = file.getOriginalFilename();
        String ext = "jpg";
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot > 0 && dot < original.length() - 1) {
                ext = original.substring(dot + 1).toLowerCase();
            }
        }
        return MakeoverConstant.OSS_ORIGIN_PREFIX + userId + "/" + yyyymm + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Makeover] JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 从 Redis 读取 AI 妆造分析缓存（faceFeatures + suggestions）。
     * 返回 null 表示缓存不存在或反序列化失败（调用方应回退到 DB）。
     */
    private Map<String, Object> readAiResultCache(Long recordId) {
        String key = String.format(MakeoverConstant.REDIS_KEY_AI_RESULT, recordId);
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) return null;
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Makeover] Redis 读取 AI 结果失败 key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> readAiResultFaceFeatures(Long recordId) {
        Map<String, Object> cache = readAiResultCache(recordId);
        if (cache == null) return null;
        Object v = cache.get("faceFeatures");
        if (v instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) m;
            return cast;
        }
        return null;
    }

    private Map<String, Object> readAiResultSuggestions(Long recordId) {
        Map<String, Object> cache = readAiResultCache(recordId);
        if (cache == null) return null;
        Object v = cache.get("suggestions");
        if (v instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) m;
            return cast;
        }
        return null;
    }

    /**
     * 从 Redis 读 summary（新 schema，老缓存无此字段时返回 null）。
     */
    private Map<String, Object> readAiResultSummary(Long recordId) {
        Map<String, Object> cache = readAiResultCache(recordId);
        if (cache == null) return null;
        Object v = cache.get("summary");
        if (v instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) m;
            // 空 Map 视为 null，前端隐藏该区域
            if (cast.isEmpty()) return null;
            return cast;
        }
        return null;
    }

    private void evictAiResultCache(Long recordId) {
        String key = String.format(MakeoverConstant.REDIS_KEY_AI_RESULT, recordId);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[Makeover] Redis 清理 AI 结果缓存失败 key={}, err={}", key, e.getMessage());
        }
    }

    private MakeoverListVO toListVO(MakeoverRecord r) {
        MakeoverListVO vo = new MakeoverListVO();
        vo.setRecordId(r.getId());
        vo.setSceneCode(r.getSceneCode());
        vo.setSceneText(r.getSceneText());
        vo.setOriginalUrl(r.getOriginalUrl());
        vo.setAfterUrl(r.getAfterUrl());
        vo.setStatus(r.getStatus());
        vo.setCreatedAt(r.getCreatedAt());
        return vo;
    }

    /**
     * 访问权限判定：仅允许记录所有者 + 记录所有者的绑定伴侣查看详情。
     * <p>
     * 设计权衡：伴侣通过「分享给 TA」流程拿到 recordId，可正常查看详情（含图像与文字建议），
     * 但删除/取消/再编辑等写操作仍走原有 owner-only 校验，不受影响。
     */
    private boolean canAccess(Integer userId, MakeoverRecord record) {
        if (record.getUserId().equals(userId)) {
            return true;
        }
        // 伴侣视角：当前用户必须是记录所有者的 partner
        User owner = userMapper.selectById(record.getUserId());
        if (owner == null || owner.getPartnerId() == null) {
            return false;
        }
        return userId.equals(Math.toIntExact(owner.getPartnerId()));
    }

    /**
     * 内存中的 MultipartFile（用于把自动缩放后的字节流重新塞回 uploadFile()）。
     * <p>
     * 仅实现 Makeover 链路用到的最小方法集；其它调用场景需自行扩展。
     */
    private static final class InMemoryMultipartFile implements MultipartFile {
        private final byte[] bytes;
        private final String name;
        private final String contentType;

        InMemoryMultipartFile(byte[] bytes, String name, String contentType) {
            this.bytes = bytes;
            this.name = name;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "image";
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes == null ? 0 : bytes.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return bytes;
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException();
        }
    }
}
