package com.example.lovemap.service;

import com.example.lovemap.common.PageResult;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.VipOrderDTO;
import com.example.lovemap.model.dto.VipOrderHistoryQueryDTO;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.VipOrderHistoryVO;
import com.example.lovemap.model.vo.VipOrderVO;
import com.example.lovemap.model.vo.VipPendingOrderVO;
import com.example.lovemap.model.vo.VipTierVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * VIP 会员 Service
 */
public interface VipService {

    /**
     * 查询全部 VIP 档位与权益
     */
    Result<List<VipTierVO>> listTiers();

    /**
     * 查询待开通订单列表（顾问侧），按下单时间升序
     */
    Result<List<VipPendingOrderVO>> listPendingOrders();

    /**
     * 查询订单历史（顾问侧，全状态，多条件分页）
     *
     * @param query 查询条件
     */
    Result<PageResult<VipOrderHistoryVO>> listOrdersHistory(VipOrderHistoryQueryDTO query);

    /**
     * 提交 VIP 下单：仅登记待开通订单，由顾问收款后手动开通
     */
    /**
     * 提交 VIP 下单。
     * <p>
     * 返回 data 可能为：
     * <ul>
     *   <li>{@link VipOrderVO}：下单成功</li>
     *   <li>{@link java.util.Map}：code=409 时返回的旧订单摘要（orderNo/vipLevel/vipLevelName/priceYuan/createdAt）</li>
     * </ul>
     */
    Result<Object> createOrder(Integer userId, VipOrderDTO dto);

    /**
     * 查询账单：本人与同组（情侣）的全部订单，下单时间倒序
     *
     * @param userId 当前用户 ID
     */
    Result<List<VipOrderVO>> listOrders(Integer userId);

    /**
     * 取消待开通订单：仅本人或同组订单可取消，已开通的订单不可取消
     *
     * @param userId  当前用户 ID
     * @param orderNo 订单号
     */
    Result<VipOrderVO> cancelOrder(Integer userId, String orderNo);

    /**
     * 顾问收款后开通 VIP：订单置为已开通，并写入用户 VIP 等级与到期时间
     */
    Result<VipOrderVO> activateOrder(String orderNo);

    /**
     * 校验顾问开通密钥；未配置密钥时一律拒绝，避免开通接口被越权调用
     */
    boolean isValidAdminToken(String token);

    /**
     * 查询当前用户的配额快照（AI 约会/心愿/导出），用于前端在推荐按钮旁展示剩余次数
     */
    com.example.lovemap.model.vo.VipQuotaVO currentQuotas(Integer userId);

    /**
     * 解析用户当前生效的 VIP
     * <p>
     * VIP 归属情侣组：取组内成员中等级最高的一方，同等级取到期更晚的一方。
     *
     * @param user 用户（可为 null）
     * @return 生效中的 VIP，未开通或已过期时等级为 0
     */
    EffectiveVip resolveEffectiveVip(User user);

    /**
     * 生效中的 VIP 信息
     *
     * @param level    生效等级，0 表示非 VIP
     * @param name     等级名称
     * @param expireAt 到期时间，永久卡为 null
     */
    record EffectiveVip(int level, String name, LocalDateTime expireAt) {
    }
}
