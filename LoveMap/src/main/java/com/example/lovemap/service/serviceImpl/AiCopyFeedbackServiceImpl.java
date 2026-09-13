package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.AiConstant;
import com.example.lovemap.mapper.AiCopyFeedbackMapper;
import com.example.lovemap.model.dto.AiCopyFeedbackDTO;
import com.example.lovemap.model.entity.AiCopyFeedback;
import com.example.lovemap.service.AiCopyFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 文案反馈服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCopyFeedbackServiceImpl implements AiCopyFeedbackService {

    private final AiCopyFeedbackMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> submitFeedback(Integer userId, AiCopyFeedbackDTO dto) {
        if (dto.getFeedback() != AiConstant.FEEDBACK_ACCEPTED
                && dto.getFeedback() != AiConstant.FEEDBACK_REJECTED) {
            return Result.badRequest("feedback 必须为 1(采纳) 或 2(拒绝)");
        }
        AiCopyFeedback fb = new AiCopyFeedback();
        fb.setUserId(userId.longValue());
        fb.setAnniversaryId(dto.getAnniversaryId());
        fb.setStyle(dto.getStyle());
        fb.setCacheKeySuffix(dto.getCacheKeySuffix());
        fb.setFeedback(dto.getFeedback());
        fb.setCopyPreview(dto.getCopyPreview());

        try {
            mapper.upsert(fb);
            log.info("用户 {} 提交 AI 文案反馈 cacheKeySuffix={} feedback={}",
                    userId, dto.getCacheKeySuffix(), dto.getFeedback());
            return Result.success(null);
        } catch (Exception e) {
            log.error("提交 AI 文案反馈失败 userId={} cacheKeySuffix={}",
                    userId, dto.getCacheKeySuffix(), e);
            return Result.error(ResultCode.INTERNAL_SERVER_ERROR, "反馈提交失败，请稍后重试");
        }
    }

    @Override
    public Result<List<StyleAcceptanceVO>> getAcceptanceStats(Integer userId) {
        List<AiCopyFeedbackMapper.StyleAcceptanceRow> rows =
                mapper.selectAcceptanceByStyle(userId.longValue());
        if (rows == null || rows.isEmpty()) {
            return Result.success(List.of());
        }
        List<StyleAcceptanceVO> out = new ArrayList<>(rows.size());
        for (AiCopyFeedbackMapper.StyleAcceptanceRow r : rows) {
            StyleAcceptanceVO vo = new StyleAcceptanceVO();
            vo.setStyle(r.getStyle());
            vo.setTotalCount(r.getTotalCount());
            vo.setAcceptedCount(r.getAcceptedCount());
            int total = r.getTotalCount() == null ? 0 : r.getTotalCount();
            int acc = r.getAcceptedCount() == null ? 0 : r.getAcceptedCount();
            vo.setAcceptanceRate(total == 0 ? 0.0 : Math.round((double) acc / total * 1000.0) / 1000.0);
            out.add(vo);
        }
        return Result.success(out);
    }
}