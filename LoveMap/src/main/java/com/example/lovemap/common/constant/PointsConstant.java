package com.example.lovemap.common.constant;

/**
 * 积分模块常量
 */
public final class PointsConstant {

    private PointsConstant() {
    }

    /** 积分变动类型 */
    public static final int CHANGE_TYPE_CHECKIN   = 1;
    public static final int CHANGE_TYPE_TASK      = 2;
    public static final int CHANGE_TYPE_EXCHANGE  = 3;
    public static final int CHANGE_TYPE_ADMIN     = 4;

    /** 关联类型 */
    public static final String REF_CHECKIN  = "check_in";
    public static final String REF_TASK     = "task";
    public static final String REF_EXCHANGE = "exchange";
    public static final String REF_ADMIN    = "admin";

    /** 商品状态 */
    public static final int PRODUCT_OFF_SHELF = 0;
    public static final int PRODUCT_ON_SHELF  = 1;

    /** 任务状态 */
    public static final int TASK_DISABLED = 0;
    public static final int TASK_ENABLED  = 1;

    /** 商品促销状态 */
    public static final int PROMO_OFF = 0;
    public static final int PROMO_ON  = 1;

    /** 兑换记录（订单）状态：0=待顾问确认收款 1=已发放 2=已取消 */
    public static final int EXCHANGE_PENDING  = 0;
    public static final int EXCHANGE_SUCCESS  = 1;
    public static final int EXCHANGE_CANCELED = 2;

    /** 用户任务状态机：0=未完成 1=已完成待领取 2=已领取 */
    public static final int USER_TASK_INCOMPLETE = 0;
    public static final int USER_TASK_COMPLETED  = 1;
    public static final int USER_TASK_CLAIMED    = 2;

    /** 任务周期类型 */
    public static final String PERIOD_DAILY   = "daily";
    public static final String PERIOD_WEEKLY  = "weekly";
    public static final String PERIOD_MONTHLY = "monthly";

    /** 任务完成方式（由哪个系统事件自动识别） */
    public static final String COMPLETE_MANUAL        = "manual";
    public static final String COMPLETE_MESSAGE_SEND  = "message_send";
    public static final String COMPLETE_PHOTO_UPLOAD  = "photo_upload";
    public static final String COMPLETE_DATE_PLAN     = "date_plan_create";

    /**
     * 签到里程碑奖励（连续 N 天时的本次签到积分）
     * 规则：连续 1~2 天按 N 发；命中里程碑则按里程碑奖励发；
     *       其他天数按 N 发；断签则连续天数清零。
     *
     * 配套需求：连续 3 天 +10；连续 7 天 +50；连续 10 天 +100；
     *           连续 20 天 +300；连续 30 天 +900。
     */
    public static final int[] MILESTONE_DAYS  = {3, 7, 10, 20, 30};
    public static final int[] MILESTONE_SCORE = {10, 50, 100, 300, 900};

    /**
     * 计算本次签到应得积分（连续 N 天）
     */
    public static int computeCheckInScore(int continuousDays) {
        if (continuousDays <= 0) {
            return 0;
        }
        // 累计模式：基础分（连续天数 N）+ 当天命中的里程碑奖励
        // 例：第 7 天 = 7 + 50 = 57；第 3 天 = 3 + 10 = 13；第 10 天 = 10 + 100 = 110
        int score = continuousDays;
        for (int i = 0; i < MILESTONE_DAYS.length; i++) {
            if (continuousDays == MILESTONE_DAYS[i]) {
                score += MILESTONE_SCORE[i];
                break;
            }
        }
        return score;
    }
}
