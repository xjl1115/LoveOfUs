package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AnniversaryDTO;
import com.example.lovemap.model.vo.AnniversaryVO;

import java.util.List;

/**
 * 纪念日服务接口
 */
public interface AnniversaryService {

    /**
     * 查询纪念日列表
     */
    Result<List<AnniversaryVO>> listAnniversaries(Integer userId);

    /**
     * 查询纪念日详情
     */
    Result<AnniversaryVO> getAnniversaryDetail(Integer userId, Long id);

    /**
     * 创建纪念日
     */
    Result<AnniversaryVO> createAnniversary(Integer userId, AnniversaryDTO dto);

    /**
     * 修改纪念日
     */
    Result<AnniversaryVO> updateAnniversary(Integer userId, Long id, AnniversaryDTO dto);

    /**
     * 删除纪念日
     */
    Result<Void> deleteAnniversary(Integer userId, Long id);
}