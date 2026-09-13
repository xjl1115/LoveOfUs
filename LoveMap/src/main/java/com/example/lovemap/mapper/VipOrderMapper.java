package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.VipOrder;
import com.example.lovemap.model.vo.VipPendingOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * VIP 订单 Mapper
 */
@Mapper
public interface VipOrderMapper {

    /**
     * 新增订单
     */
    int insert(VipOrder order);

    /**
     * 按订单号查询
     */
    VipOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 更新订单状态
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("activatedAt") LocalDateTime activatedAt);

    /**
     * 把该用户的待开通订单全部置为已取消
     */
    int cancelPendingOrders(@Param("userId") Long userId,
                            @Param("pendingStatus") Integer pendingStatus,
                            @Param("canceledStatus") Integer canceledStatus);

    /**
     * 查询全部待开通订单（含下单用户昵称），按下单时间升序
     */
    List<VipPendingOrderVO> selectPendingOrders(@Param("pendingStatus") Integer pendingStatus);

    /**
     * 查询订单列表：本人订单 + 同组订单（双人账单合并展示）
     * <p>
     * 始终带上 user_id，避免组队前下的订单（group_id 为空）在下单人自己的账单里消失。
     *
     * @param userId  当前用户 ID
     * @param groupId 当前用户的情侣组 ID，可为 null
     */
    List<VipOrder> selectByUserOrGroup(@Param("userId") Long userId,
                                       @Param("groupId") Long groupId);

    /**
     * 取消待开通订单：仅订单仍是待开通状态时生效，返回受影响行数
     * <p>
     * 条件更新兜住"用户点取消的同时顾问点了开通"的并发，返回 0 表示订单状态已变更。
     */
    int cancelPendingOrder(@Param("id") Long id,
                           @Param("pendingStatus") Integer pendingStatus,
                           @Param("canceledStatus") Integer canceledStatus);
}
