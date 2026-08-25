package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.ExportDTO;
import com.example.lovemap.model.vo.ExportRecordVO;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * 导出服务接口
 */
public interface ExportService {

    /**
     * 创建导出任务
     */
    Result<ExportRecordVO> createExport(Integer userId, ExportDTO dto);

    /**
     * 获取导出任务状态
     */
    Result<ExportRecordVO> getExportStatus(Integer userId, Long id);

    /**
     * 获取用户的导出任务列表
     */
    Result<List<ExportRecordVO>> getExportHistory(Integer userId);

    /**
     * 下载导出文件
     */
    org.springframework.http.ResponseEntity<Resource> downloadExport(Integer userId, Long id);

    /**
     * 取消导出任务
     */
    Result<Void> cancelExport(Integer userId, Long id);
}