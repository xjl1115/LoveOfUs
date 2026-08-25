package com.example.lovemap.common;

import lombok.Data;

import java.util.List;

/**
 * 分页结果
 *
 * @param <T> 数据类型
 */
@Data
public class PageResult<T> {

    /**
     * 数据列表
     */
    private List<T> list;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码
     */
    private int page;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 总页数
     */
    private int pages;

    /**
     * 是否有下一页
     */
    private boolean hasNext;

    /**
     * 是否有上一页
     */
    private boolean hasPrevious;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = (int) Math.ceil((double) total / size);
        this.hasNext = page < this.pages;
        this.hasPrevious = page > 1;
    }

    /**
     * 创建空分页结果
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0, page, size);
    }
}
