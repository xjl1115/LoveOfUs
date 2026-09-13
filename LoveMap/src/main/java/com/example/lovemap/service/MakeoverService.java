package com.example.lovemap.service;

import com.example.lovemap.common.PageResult;
import com.example.lovemap.model.dto.MakeoverCreateDTO;
import com.example.lovemap.model.vo.MakeoverCreateVO;
import com.example.lovemap.model.vo.MakeoverDetailVO;
import com.example.lovemap.model.vo.MakeoverListVO;
import com.example.lovemap.model.vo.MakeoverQuotaVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 化妆建议服务
 */
public interface MakeoverService {

    /**
     * 创建化妆建议任务（同步落库 + 返回 recordId，异步执行）
     */
    MakeoverCreateVO create(Integer userId, MultipartFile image, MakeoverCreateDTO dto);

    /**
     * 查询本月化妆建议额度（免费额度 + 生效 VIP 档位的额外额度）
     */
    MakeoverQuotaVO quota(Integer userId);

    /**
     * 查询详情
     */
    MakeoverDetailVO detail(Integer userId, Long id);

    /**
     * 分页查询用户历史
     */
    PageResult<MakeoverListVO> list(Integer userId, int page, int size);

    /**
     * 软删除
     */
    void softDelete(Integer userId, Long id);

    /**
     * 取消任务（仅在 pending / analyzing 时可取消）
     */
    void cancel(Integer userId, Long id);
}
