package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.PointProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分商品 Mapper
 */
@Mapper
public interface PointProductMapper {

    /** 列出所有商品（顾问控制台用，含下架） */
    List<PointProduct> selectAll();

    /** 列出已上架商品（前端用户只查已上架） */
    List<PointProduct> selectOnShelf();

    /** 按 ID 查询 */
    PointProduct selectById(@Param("id") Long id);

    /** 新增 */
    int insert(PointProduct product);

    /** 全量更新（顾问控制台保存） */
    int update(PointProduct product);

    /** 删除（物理删除；保留 point_exchange 历史引用 product_name 字段） */
    int deleteById(@Param("id") Long id);

    /**
     * 原子扣减库存（带条件：未下架 + 库存充足 或 stock = -1 不限）
     *
     * @return 受影响行数（0 = 扣减失败：库存不足 / 已下架 / 商品不存在）
     */
    int decrStock(@Param("id") Long id);
}
