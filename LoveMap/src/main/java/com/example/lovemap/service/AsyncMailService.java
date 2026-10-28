package com.example.lovemap.service;

import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 异步邮件服务
 */
@Service
@Slf4j
public class AsyncMailService {

    @Resource
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Getter
    @Value("${verify.code.expire:60}")
    private Integer expire;

    @Value("${app.name:LoveOfUs}")
    private String senderName;

    /**
     * 异步发送验证码邮件
     */
    @Async("verifyCodeExecutor")
    public void sendVerifyCodeMailAsync(String to, String code, String type, Integer expire) {
        String typeName = switch (type) {
            case "register" -> "注册";
            case "login" -> "登录";
            case "reset_password" -> "重置密码";
            case "bind_email" -> "绑定邮箱";
            default -> "验证";
        };
        long startTime = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject("【" + senderName + "】" + typeName + "验证码");
            helper.setText(buildEmailContent(typeName, code, expire), true);
            javaMailSender.send(mimeMessage);
            log.info("异步发送验证码邮件成功, 耗时{}ms, 收件人:{}", System.currentTimeMillis() - startTime, to);
        } catch (Exception e) {
            log.error("异步发送验证码邮件失败, 耗时{}ms, 收件人:{}, 错误:{}", System.currentTimeMillis() - startTime, to, e.getMessage());
        }
    }

    /**
     * 异步发送系统通知邮件
     *
     * @param to         收件人邮箱
     * @param nickname   用户昵称
     * @param actor      行动者（如：您的伴侣）
     * @param action     做了什么动作（如：上传了一张新照片）
     */
    @Async("verifyCodeExecutor")
    public void sendSystemNotificationMailAsync(String to, String nickname, String actor, String action) {
        long startTime = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject("【" + senderName + "】新消息通知");
            helper.setText(buildNotificationContent(nickname, actor, action), true);
            javaMailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("异步发送系统通知邮件失败, 耗时{}ms, 收件人:{}, 错误:{}", System.currentTimeMillis() - startTime, to, e.getMessage());
        }
    }

    /**
     * 异步发送 VIP 下单通知邮件给专属顾问
     *
     * @param to        顾问邮箱
     * @param nickname  下单用户昵称
     * @param phone     下单用户手机号（明文）
     * @param email     下单用户邮箱
     * @param tierName  VIP 档位名称
     * @param priceYuan 档位价格（元）
     * @param orderNo   订单号
     */
    @Async("verifyCodeExecutor")
    public void sendVipOrderMailAsync(String to, String nickname, String phone, String email,
                                      String tierName, BigDecimal priceYuan, String orderNo) {
        long startTime = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject("【" + senderName + "】VIP 开通申请 - " + nickname + " - " + tierName);
            helper.setText(buildVipOrderContent(nickname, phone, email, tierName, priceYuan, orderNo), true);
            javaMailSender.send(mimeMessage);
            log.info("VIP 开通申请邮件发送成功, 耗时{}ms, 收件人:{}, 订单号:{}", System.currentTimeMillis() - startTime, to, orderNo);
        } catch (Exception e) {
            log.error("VIP 开通申请邮件发送失败, 耗时{}ms, 收件人:{}, 订单号:{}, 错误:{}", System.currentTimeMillis() - startTime, to, orderNo, e.getMessage());
        }
    }

    /**
     * 异步发送次卡开通申请邮件（与 VIP 卡流程一致，由顾问收款后开通）
     */
    @Async("verifyCodeExecutor")
    public void sendQuotaOrderMailAsync(String to, String nickname, String phone, String email,
                                        String quotaTypeName, Integer quotaCount, BigDecimal priceYuan, String orderNo) {
        long startTime = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject("【" + senderName + "】次卡开通申请 - " + nickname + " - " + quotaTypeName + " +" + quotaCount);
            helper.setText(buildQuotaOrderContent(nickname, phone, email, quotaTypeName, quotaCount, priceYuan, orderNo), true);
            javaMailSender.send(mimeMessage);
            log.info("次卡开通申请邮件发送成功, 耗时{}ms, 收件人:{}, 订单号:{}", System.currentTimeMillis() - startTime, to, orderNo);
        } catch (Exception e) {
            log.error("次卡开通申请邮件发送失败, 耗时{}ms, 收件人:{}, 订单号:{}, 错误:{}", System.currentTimeMillis() - startTime, to, orderNo, e.getMessage());
        }
    }

    /**
     * 异步发送积分商品订单通知邮件给专属顾问
     *
     * @param to          顾问邮箱
     * @param nickname    下单用户昵称
     * @param phone       下单用户手机号
     * @param email       下单用户邮箱
     * @param productName 商品名称
     * @param pointsCost  单价积分
     * @param quantity    兑换数量
     * @param amountYuan  需付款金额（元）
     * @param orderNo     订单号
     */
    @Async("verifyCodeExecutor")
    public void sendPointOrderMailAsync(String to, String nickname, String phone, String email,
                                        String productName, Integer pointsCost, Integer quantity,
                                        BigDecimal amountYuan, String orderNo,
                                        String receiverName, String shippingAddress) {
        long startTime = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject("【" + senderName + "】积分商品兑换申请 - " + nickname + " - " + productName);
            helper.setText(buildPointOrderContent(nickname, phone, email, productName,
                    pointsCost, quantity, amountYuan, orderNo, receiverName, shippingAddress), true);
            javaMailSender.send(mimeMessage);
            log.info("积分商品订单邮件发送成功, 耗时{}ms, 收件人:{}, 订单号:{}",
                    System.currentTimeMillis() - startTime, to, orderNo);
        } catch (Exception e) {
            log.error("积分商品订单邮件发送失败, 耗时{}ms, 收件人:{}, 订单号:{}, 错误:{}",
                    System.currentTimeMillis() - startTime, to, orderNo, e.getMessage());
        }
    }

    /**
     * 构建积分商品订单通知邮件内容（HTML）
     */
    private String buildPointOrderContent(String nickname, String phone, String email,
                                          String productName, Integer pointsCost, Integer quantity,
                                          BigDecimal amountYuan, String orderNo,
                                          String receiverName, String shippingAddress) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String safePhone = phone == null ? "-" : phone;
        String safeEmail = email == null ? "-" : email;
        String safeReceiver = (receiverName == null ? "-" : receiverName)
                + " / " + (shippingAddress == null ? "-" : shippingAddress);
        int totalPoints = (pointsCost == null ? 0 : pointsCost) * (quantity == null ? 1 : quantity);
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #e74c3c;\">" +
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveOfUs</h1>" +
                        "<p style=\"color: #888888; margin: 10px 0 0 0; font-size: 14px;\">记录我们的每一刻</p>" +
                        "</div>" +
                        "<div style=\"padding: 40px 30px;\">" +
                        "<h2 style=\"color: #333333; margin: 0 0 20px 0; font-size: 20px; font-weight: 600; border-left: 4px solid #e74c3c; padding-left: 12px;\">积分商品兑换申请</h2>" +
                        "<div style=\"background: #fff5f5; border-radius: 12px; padding: 25px; margin: 20px 0;\">" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "用户：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "商品：<span style=\"color: #e74c3c; font-weight: bold;\">%s × %d</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "需付款：<span style=\"color: #e74c3c; font-weight: bold;\">%s 元</span>，抵扣积分：%d" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "手机号：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "邮箱：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "收货地址：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0;\">" +
                        "订单号：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "</div>" +
                        "<div style=\"background: #f0f9ff; border-radius: 12px; padding: 20px 25px; margin: 20px 0;\">" +
                        "<p style=\"color: #333333; font-size: 15px; font-weight: 600; margin: 0 0 8px 0;\">📌 发放方式</p>" +
                        "<p style=\"color: #666666; font-size: 14px; line-height: 1.8; margin: 0;\">" +
                        "请登录顾问后台：<span style=\"color: #e74c3c; font-weight: bold;\">/vip-admin.html</span>，" +
                        "在「待确认兑换」列表中找到该订单，确认收款后点击「确认发放」完成积分扣减与商品发放。" +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveOfUs 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                nickname, productName, quantity == null ? 1 : quantity, amountYuan, totalPoints,
                safePhone, safeEmail, safeReceiver, orderNo, time
        );
    }

    /**
     * 异步发送积分商品兑换成功邮件（发给兑换用户本人）
     * <p>
     * 由 {@link EmailNotificationService#sendPointExchangeSuccessEmail} 在用户开启邮箱通知时调用。
     *
     * @param to          收件人邮箱
     * @param nickname    收件人昵称
     * @param productName 商品名称（订单中记录的真实名称）
     * @param quantity    兑换数量
     * @param totalPoints 扣除积分
     * @param amountYuan  付款金额（元）
     * @param orderNo     订单号
     */
    @Async("verifyCodeExecutor")
    public void sendPointExchangeSuccessMailAsync(String to, String nickname, String productName,
                                                  Integer quantity, Integer totalPoints,
                                                  BigDecimal amountYuan, String orderNo) {
        long startTime = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject("【" + senderName + "】积分商品兑换成功 - " + productName);
            helper.setText(buildPointExchangeSuccessContent(nickname, productName, quantity,
                    totalPoints, amountYuan, orderNo), true);
            javaMailSender.send(mimeMessage);
            log.info("积分商品兑换成功邮件发送成功, 耗时{}ms, 收件人:{}, 订单号:{}",
                    System.currentTimeMillis() - startTime, to, orderNo);
        } catch (Exception e) {
            log.error("积分商品兑换成功邮件发送失败, 耗时{}ms, 收件人:{}, 订单号:{}, 错误:{}",
                    System.currentTimeMillis() - startTime, to, orderNo, e.getMessage());
        }
    }

    /**
     * 构建积分商品兑换成功邮件内容（HTML格式）
     */
    private String buildPointExchangeSuccessContent(String nickname, String productName, Integer quantity,
                                                    Integer totalPoints, BigDecimal amountYuan, String orderNo) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String amountText = amountYuan == null ? "0" : amountYuan.stripTrailingZeros().toPlainString();
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #e74c3c;\">" +
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveOfUs</h1>" +
                        "<p style=\"color: #888888; margin: 10px 0 0 0; font-size: 14px;\">记录我们的每一刻</p>" +
                        "</div>" +
                        "<div style=\"padding: 40px 30px;\">" +
                        "<p style=\"color: #333333; font-size: 16px; line-height: 1.8; margin: 0 0 20px 0;\">" +
                        "<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>，您好！" +
                        "</p>" +
                        "<div style=\"background: #fff5f5; border-radius: 12px; padding: 25px; margin: 20px 0; text-align: center;\">" +
                        "<p style=\"font-size: 20px; color: #e74c3c; margin: 0 0 15px 0;\">🎁 积分商品兑换成功</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0;\">" +
                        "兑换商品：<strong style=\"color: #e74c3c;\">%s × %d</strong>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 10px 0 0 0;\">" +
                        "扣除积分：<strong>%d 积分</strong><br>" +
                        "付款金额：<strong>%s 元</strong>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 10px 0 0 0;\">" +
                        "订单号：<strong>%s</strong><br>" +
                        "确认时间：<strong>%s</strong>" +
                        "</p>" +
                        "</div>" +
                        "<p style=\"color: #999999; font-size: 13px; margin: 20px 0;\">" +
                        "商品将由专属顾问为您安排发放，请注意查收～" +
                        "</p>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveOfUs 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                nickname, productName, quantity == null ? 1 : quantity,
                totalPoints == null ? 0 : totalPoints, amountText, orderNo, time, time
        );
    }

    /**
     * 异步发送 VIP 开通成功邮件
     *
     * @param to              收件人邮箱
     * @param nickname        收件人昵称
     * @param tierName        VIP 档位名称
     * @param activatedAtText 开通时间（已格式化）
     * @param expireAtText    到期时间（已格式化，永久卡为“永久有效”）
     */
    @Async("verifyCodeExecutor")
    public void sendVipActivatedMailAsync(String to, String nickname, String tierName,
                                          String activatedAtText, String expireAtText) {
        long startTime = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject("【" + senderName + "】VIP 开通成功 - " + tierName);
            helper.setText(buildVipActivatedContent(nickname, tierName, activatedAtText, expireAtText), true);
            javaMailSender.send(mimeMessage);
            log.info("VIP 开通成功邮件发送成功, 耗时{}ms, 收件人:{}, 档位:{}", System.currentTimeMillis() - startTime, to, tierName);
        } catch (Exception e) {
            log.error("VIP 开通成功邮件发送失败, 耗时{}ms, 收件人:{}, 错误:{}", System.currentTimeMillis() - startTime, to, e.getMessage());
        }
    }

    /**
     * 异步发送卡类价格调整通知邮件
     * <p>
     * 仅在用户勾选“邮箱通知”时由 {@link EmailNotificationService} 调用。
     *
     * @param to       收件人邮箱
     * @param nickname 收件人昵称
     * @param content  价格变更内容（与系统通知文案一致）
     */
    @Async("verifyCodeExecutor")
    public void sendCardPriceChangedMailAsync(String to, String nickname, String content) {
        long startTime = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject("【" + senderName + "】卡类价格调整通知");
            helper.setText(buildPriceChangeContent(nickname, content), true);
            javaMailSender.send(mimeMessage);
            log.info("卡类价格调整邮件发送成功, 耗时{}ms, 收件人:{}", System.currentTimeMillis() - startTime, to);
        } catch (Exception e) {
            log.error("卡类价格调整邮件发送失败, 耗时{}ms, 收件人:{}, 错误:{}", System.currentTimeMillis() - startTime, to, e.getMessage());
        }
    }

    /**
     * 构建卡类价格调整邮件内容（HTML格式）
     *
     * @param nickname 收件人昵称
     * @param content  价格变更内容（纯文本，换行按 HTML 渲染）
     */
    private String buildPriceChangeContent(String nickname, String content) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String bodyHtml = content == null ? "" : content.replace("\n", "<br>");
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #e74c3c;\">" +
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveOfUs</h1>" +
                        "<p style=\"color: #888888; margin: 10px 0 0 0; font-size: 14px;\">记录我们的每一刻</p>" +
                        "</div>" +
                        "<div style=\"padding: 40px 30px;\">" +
                        "<h2 style=\"color: #333333; margin: 0 0 20px 0; font-size: 20px; font-weight: 600; border-left: 4px solid #e74c3c; padding-left: 12px;\">卡类价格调整通知</h2>" +
                        "<p style=\"color: #333333; font-size: 16px; line-height: 1.8; margin: 0 0 20px 0;\">" +
                        "<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>，您好！" +
                        "</p>" +
                        "<div style=\"background: #fff5f5; border-radius: 12px; padding: 25px; margin: 20px 0;\">" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0;\">%s</p>" +
                        "</div>" +
                        "<p style=\"color: #999999; font-size: 13px; margin: 20px 0;\">" +
                        "新价格已在 App 开卡页生效，具体以开卡页展示为准。" +
                        "</p>" +
                        "<div style=\"background: #f9f9f9; border: 1px solid #eeeeee; border-radius: 8px; padding: 15px; margin: 20px 0;\">" +
                        "<p style=\"color: #888888; font-size: 13px; margin: 0;\">" +
                        "💡 如需关闭此类邮件，请在 App「我的 - 消息通知设置」中关闭「邮箱通知」。" +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveOfUs 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                nickname, bodyHtml, time
        );
    }
    /**
     * 构建系统通知邮件内容（HTML格式）
     */
    private String buildNotificationContent(String nickname, String actor, String action) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #e74c3c;\">" +
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveOfUs</h1>" +
                        "<p style=\"color: #888888; margin: 10px 0 0 0; font-size: 14px;\">记录我们的每一刻</p>" +
                        "</div>" +
                        "<div style=\"padding: 40px 30px;\">" +
                        "<p style=\"color: #333333; font-size: 16px; line-height: 1.8; margin: 0 0 20px 0;\">" +
                        "<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>，您好！" +
                        "</p>" +
                        "<div style=\"background: #fff5f5; border-radius: 12px; padding: 25px; margin: 20px 0;\">" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0;\">" +
                        "<span style=\"color: #e74c3c; font-weight: bold;\">%s</span> 于 <span style=\"color: #888888;\">%s</span> %s" +
                        "</p>" +
                        "</div>" +
                        "<p style=\"color: #999999; font-size: 13px; margin: 20px 0;\">" +
                        "快打开 LoveOfUs 查看详细内容吧！" +
                        "</p>" +
                        "<div style=\"background: #f9f9f9; border: 1px solid #eeeeee; border-radius: 8px; padding: 15px; margin: 20px 0;\">" +
                        "<p style=\"color: #888888; font-size: 13px; margin: 0;\">" +
                        "💡 如需关闭此类通知，请在应用设置中调整通知偏好。" +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveOfUs 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                nickname, actor, time, action, time
        );
    }

    /**
     * 异步发送纪念日提醒邮件
     *
     * @param to              收件人邮箱
     * @param nickname        用户昵称
     * @param anniversaryName 纪念日名称
     * @param daysUntil       距离纪念日天数（0=今天）
     * @param description     纪念日描述（可为null）
     */
    @Async("verifyCodeExecutor")
    public void sendAnniversaryReminderMailAsync(String to, String nickname, String anniversaryName, long daysUntil, String description) {
        long startTime = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject("【" + senderName + "】纪念日提醒 - " + anniversaryName);
            helper.setText(buildAnniversaryReminderContent(nickname, anniversaryName, daysUntil, description), true);
            javaMailSender.send(mimeMessage);
            log.info("异步发送纪念日提醒邮件成功, 耗时{}ms, 收件人:{}, 纪念日:{}", System.currentTimeMillis() - startTime, to, anniversaryName);
        } catch (Exception e) {
            log.error("异步发送纪念日提醒邮件失败, 耗时{}ms, 收件人:{}, 错误:{}", System.currentTimeMillis() - startTime, to, e.getMessage());
        }
    }

    /**
     * 构建纪念日提醒邮件内容（HTML格式）
     */
    private String buildAnniversaryReminderContent(String nickname, String anniversaryName, long daysUntil, String description) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String reminderText;
        if (daysUntil == 0) {
            reminderText = "今天就是你们的 <strong style=\"color: #e74c3c;\">" + anniversaryName + "</strong> 啦！记得给TA一个惊喜哦~";
        } else {
            reminderText = "距离 <strong style=\"color: #e74c3c;\">" + anniversaryName + "</strong> 还有 <strong style=\"color: #e74c3c;\">" + daysUntil + "</strong> 天，提前准备一下吧！";
        }
        // 构建描述部分（有描述时才展示）
        String descriptionBlock = "";
        if (description != null && !description.isEmpty()) {
            descriptionBlock = "<p style=\"color: #888888; font-size: 14px; line-height: 1.8; margin: 15px 0 0 0; font-style: italic;\">"
                    + "📝 " + description + "</p>";
        }
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #e74c3c;\">" +
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveOfUs</h1>" +
                        "<p style=\"color: #888888; margin: 10px 0 0 0; font-size: 14px;\">记录我们的每一刻</p>" +
                        "</div>" +
                        "<div style=\"padding: 40px 30px;\">" +
                        "<p style=\"color: #333333; font-size: 16px; line-height: 1.8; margin: 0 0 20px 0;\">" +
                        "<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>，您好！" +
                        "</p>" +
                        "<div style=\"background: #fff5f5; border-radius: 12px; padding: 25px; margin: 20px 0; text-align: center;\">" +
                        "<p style=\"font-size: 20px; color: #e74c3c; margin: 0 0 10px 0;\">🎉 纪念日提醒</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0;\">" +
                        reminderText +
                        "</p>" +
                        descriptionBlock +
                        "</div>" +
                        "<p style=\"color: #999999; font-size: 13px; margin: 20px 0;\">" +
                        "快打开 LoveOfUs 为TA准备一份特别的惊喜吧！" +
                        "</p>" +
                        "<div style=\"background: #f9f9f9; border: 1px solid #eeeeee; border-radius: 8px; padding: 15px; margin: 20px 0;\">" +
                        "<p style=\"color: #888888; font-size: 13px; margin: 0;\">" +
                        "💡 如需关闭此类通知，请在应用设置中调整通知偏好。" +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveOfUs 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                nickname, time
        );
    }

    /**
     * 构建 VIP 下单通知邮件内容（HTML格式）
     * <p>
     * 顾问确认收款后登录后台 vip-admin.html 完成开通，邮件内不再附带 curl 命令。
     */
    private String buildVipOrderContent(String nickname, String phone, String email,
                                        String tierName, BigDecimal priceYuan, String orderNo) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String safePhone = phone == null ? "-" : phone;
        String safeEmail = email == null ? "-" : email;
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #e74c3c;\">" +
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveOfUs</h1>" +
                        "<p style=\"color: #888888; margin: 10px 0 0 0; font-size: 14px;\">记录我们的每一刻</p>" +
                        "</div>" +
                        "<div style=\"padding: 40px 30px;\">" +
                        "<h2 style=\"color: #333333; margin: 0 0 20px 0; font-size: 20px; font-weight: 600; border-left: 4px solid #e74c3c; padding-left: 12px;\">VIP 开通申请</h2>" +
                        "<div style=\"background: #fff5f5; border-radius: 12px; padding: 25px; margin: 20px 0;\">" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "用户：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>，正在开通 VIP 服务" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "等级：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>（%s 元）" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "手机号：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "邮箱：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0;\">" +
                        "订单号：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "</div>" +
                        "<div style=\"background: #f0f9ff; border-radius: 12px; padding: 20px 25px; margin: 20px 0;\">" +
                        "<p style=\"color: #333333; font-size: 15px; font-weight: 600; margin: 0 0 8px 0;\">📌 开通方式</p>" +
                        "<p style=\"color: #666666; font-size: 14px; line-height: 1.8; margin: 0;\">" +
                        "请登录顾问后台：<span style=\"color: #e74c3c; font-weight: bold;\">/vip-admin.html</span>，" +
                        "在「待开通订单」列表中找到该订单并点击「开通」按钮完成开通。" +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveOfUs 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                nickname, tierName, priceYuan, safePhone, safeEmail, orderNo, time
        );
    }

    /**
     * 构建次卡开通申请邮件内容（HTML）
     */
    private String buildQuotaOrderContent(String nickname, String phone, String email,
                                          String quotaTypeName, Integer quotaCount, BigDecimal priceYuan, String orderNo) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String safePhone = phone == null ? "-" : phone;
        String safeEmail = email == null ? "-" : email;
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #e74c3c;\">" +
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveOfUs</h1>" +
                        "<p style=\"color: #888888; margin: 10px 0 0 0; font-size: 14px;\">记录我们的每一刻</p>" +
                        "</div>" +
                        "<div style=\"padding: 40px 30px;\">" +
                        "<h2 style=\"color: #333333; margin: 0 0 20px 0; font-size: 20px; font-weight: 600; border-left: 4px solid #e74c3c; padding-left: 12px;\">次卡开通申请</h2>" +
                        "<div style=\"background: #fff5f5; border-radius: 12px; padding: 25px; margin: 20px 0;\">" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "用户：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "类型：<span style=\"color: #e74c3c; font-weight: bold;\">%s + %d 次</span>（%s 元）" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "手机号：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0 0 10px 0;\">" +
                        "邮箱：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0;\">" +
                        "订单号：<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>" +
                        "</p>" +
                        "</div>" +
                        "<div style=\"background: #f0f9ff; border-radius: 12px; padding: 20px 25px; margin: 20px 0;\">" +
                        "<p style=\"color: #333333; font-size: 15px; font-weight: 600; margin: 0 0 8px 0;\">📌 开通方式</p>" +
                        "<p style=\"color: #666666; font-size: 14px; line-height: 1.8; margin: 0;\">" +
                        "请登录顾问后台：<span style=\"color: #e74c3c; font-weight: bold;\">/vip-admin.html</span>，" +
                        "在「待开通订单」列表中找到该订单并点击「开通」按钮完成开通，开通后用户将立即收到对应次数。" +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveOfUs 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                nickname, quotaTypeName, quotaCount, priceYuan, safePhone, safeEmail, orderNo, time
        );
    }

    /**
     * 构建 VIP 开通成功邮件内容（HTML格式）
     */
    private String buildVipActivatedContent(String nickname, String tierName,
                                            String activatedAtText, String expireAtText) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #d4af37;\">" +
                        "<h1 style=\"color: #d4af37; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveOfUs</h1>" +
                        "<p style=\"color: #888888; margin: 10px 0 0 0; font-size: 14px;\">记录我们的每一刻</p>" +
                        "</div>" +
                        "<div style=\"padding: 40px 30px;\">" +
                        "<p style=\"color: #333333; font-size: 16px; line-height: 1.8; margin: 0 0 20px 0;\">" +
                        "<span style=\"color: #d4af37; font-weight: bold;\">%s</span>，您好！" +
                        "</p>" +
                        "<div style=\"background: #fffbf0; border-radius: 12px; padding: 25px; margin: 20px 0; text-align: center;\">" +
                        "<p style=\"font-size: 20px; color: #d4af37; margin: 0 0 15px 0;\">🎉 VIP 开通成功</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 0;\">" +
                        "恭喜您开通 <strong style=\"color: #d4af37;\">%s</strong>，情侣双人共享全部权益" +
                        "</p>" +
                        "<p style=\"color: #666666; font-size: 15px; line-height: 1.8; margin: 10px 0 0 0;\">" +
                        "开通时间：<strong>%s</strong><br>" +
                        "到期时间：<strong>%s</strong>" +
                        "</p>" +
                        "</div>" +
                        "<p style=\"color: #999999; font-size: 13px; margin: 20px 0;\">" +
                        "快打开 LoveOfUs 体验您的专属权益吧！" +
                        "</p>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveOfUs 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                nickname, tierName, activatedAtText, expireAtText, time
        );
    }

    /**
     * 构建验证码邮件内容（HTML格式）
     */
    private String buildEmailContent(String typeName, String code, Integer expire) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #e74c3c;\">" +
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveOfUs</h1>" +
                        "<p style=\"color: #888888; margin: 10px 0 0 0; font-size: 14px;\">记录我们的每一刻</p>" +
                        "</div>" +
                        "<div style=\"padding: 40px 30px;\">" +
                        "<h2 style=\"color: #333333; margin: 0 0 20px 0; font-size: 20px; font-weight: 600; border-left: 4px solid #e74c3c; padding-left: 12px;\">%s验证码</h2>" +
                        "<p style=\"color: #666666; font-size: 14px; line-height: 1.8; margin: 0 0 30px 0;\">" +
                        "您正在进行<span style=\"color: #e74c3c; font-weight: bold;\">%s</span>操作，请使用以下验证码完成验证：" +
                        "</p>" +
                        "<div style=\"background: #fff5f5; border: 2px dashed #e74c3c; border-radius: 12px; padding: 25px; margin: 20px 0; text-align: center;\">" +
                        "<p style=\"font-size: 36px; font-weight: bold; color: #e74c3c; margin: 0; letter-spacing: 10px;\">%s</p>" +
                        "</div>" +
                        "<p style=\"color: #999999; font-size: 13px; margin: 20px 0;\">" +
                        "⏰ 验证码将于 <strong style=\"color: #e74c3c;\">%d秒</strong> 后过期，请尽快使用" +
                        "</p>" +
                        "<div style=\"background: #f9f9f9; border: 1px solid #eeeeee; border-radius: 8px; padding: 15px; margin: 20px 0;\">" +
                        "<p style=\"color: #888888; font-size: 13px; margin: 0;\">" +
                        "⚠️ 安全提示：请勿将验证码泄露给他人，如非本人操作请忽略此邮件。" +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveOfUs 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                typeName, typeName, code, expire, time
        );
    }
}
