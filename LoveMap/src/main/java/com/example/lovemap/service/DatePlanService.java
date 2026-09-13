package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AiRecommendRequestDTO;
import com.example.lovemap.model.dto.DatePlanCreateDTO;
import com.example.lovemap.model.dto.DatePlanStatusDTO;
import com.example.lovemap.model.dto.DatePlanUpdateDTO;
import com.example.lovemap.model.vo.AiRecommendVO;
import com.example.lovemap.model.vo.DatePlanVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 约会计划服务接口
 */
public interface DatePlanService {

    /**
     * 查询当前 group 的约会计划列表
     */
    Result<List<DatePlanVO>> listDatePlans(Integer userId);

    /**
     * 查询约会计划详情
     */
    Result<DatePlanVO> getDatePlanDetail(Integer userId, Long id);

    /**
     * 创建约会计划
     */
    Result<DatePlanVO> createDatePlan(Integer userId, DatePlanCreateDTO dto);

    /**
     * 更新约会计划
     */
    Result<DatePlanVO> updateDatePlan(Integer userId, Long id, DatePlanUpdateDTO dto);

    /**
     * 变更约会计划状态
     */
    Result<DatePlanVO> updateDatePlanStatus(Integer userId, Long id, DatePlanStatusDTO dto);

    /**
     * 上传约会照片（追加到该约会计划，完成约会时可选用）
     */
    Result<DatePlanVO> uploadDatePlanPhoto(Integer userId, Long id, MultipartFile file);

    /**
     * 删除约会计划
     */
    Result<Void> deleteDatePlan(Integer userId, Long id);

    /**
     * AI 推荐约会计划
     */
    Result<AiRecommendVO> recommendDatePlans(Integer userId, AiRecommendRequestDTO dto);
}
