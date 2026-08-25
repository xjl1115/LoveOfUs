package com.example.lovemap.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 照片导出请求DTO
 */
@Data
public class ExportDTO {

    /**
     * 导出类型：all-全部，album-指定相册，date-日期范围，selected-选中照片
     */
    private String exportType;

    /**
     * 相册ID（album类型时必填）
     */
    private Long albumId;

    /**
     * 开始日期
     */
    private LocalDate startDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

    /**
     * 选中的照片ID列表（selected类型时必填）
     */
    private List<Long> photoIds;

    /**
     * 是否包含原图
     */
    private Boolean includeOriginal;

    /**
     * 导出格式：zip-压缩包，pdf-PDF相册
     */
    private String format;

    // ========== ZIP 专属选项 ==========

    /**
     * 是否按日期分文件夹（兼容旧字段，true 时等同于 groupBy=takenDate）
     */
    private Boolean groupByDate;

    /**
     * 分组维度：none-不分组 / takenDate-按拍摄日期 / createdAt-按上传存储时间
     * 默认 takenDate；显式传值时优先
     */
    private String groupBy;

    /**
     * 是否包含元数据
     */
    private Boolean includeMetadata;

    // ========== 筛选条件 ==========

    /**
     * 省份筛选
     */
    private String province;

    /**
     * 城市筛选
     */
    private String city;

    // ========== PDF 专属选项 ==========

    /**
     * PDF 每页照片数：1/2/4/6/9
     */
    private Integer photosPerPage;

    /**
     * PDF 封面样式：simple-简约 / romantic-浪漫
     */
    private String coverStyle;

    /**
     * PDF 是否包含照片描述
     */
    private Boolean includeDescription;
}