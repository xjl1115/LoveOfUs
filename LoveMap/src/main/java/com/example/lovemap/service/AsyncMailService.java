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

    @Value("${app.name:LoveMap}")
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
     * 构建系统通知邮件内容（HTML格式）
     */
    private String buildNotificationContent(String nickname, String actor, String action) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format(
                "<div style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"padding: 30px; text-align: center; border-bottom: 2px solid #e74c3c;\">" +
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveMap</h1>" +
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
                        "快打开 LoveMap 查看详细内容吧！" +
                        "</p>" +
                        "<div style=\"background: #f9f9f9; border: 1px solid #eeeeee; border-radius: 8px; padding: 15px; margin: 20px 0;\">" +
                        "<p style=\"color: #888888; font-size: 13px; margin: 0;\">" +
                        "💡 如需关闭此类通知，请在应用设置中调整通知偏好。" +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveMap 自动发送，请勿回复<br>" +
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
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveMap</h1>" +
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
                        "快打开 LoveMap 为TA准备一份特别的惊喜吧！" +
                        "</p>" +
                        "<div style=\"background: #f9f9f9; border: 1px solid #eeeeee; border-radius: 8px; padding: 15px; margin: 20px 0;\">" +
                        "<p style=\"color: #888888; font-size: 13px; margin: 0;\">" +
                        "💡 如需关闭此类通知，请在应用设置中调整通知偏好。" +
                        "</p>" +
                        "</div>" +
                        "</div>" +
                        "<div style=\"padding: 20px; text-align: center; border-top: 1px solid #eeeeee;\">" +
                        "<p style=\"color: #bbbbbb; font-size: 12px; margin: 0;\">" +
                        "此邮件由 LoveMap 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                nickname, time
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
                        "<h1 style=\"color: #e74c3c; margin: 0; font-size: 26px; font-weight: 600; letter-spacing: 2px;\">❤ LoveMap</h1>" +
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
                        "此邮件由 LoveMap 自动发送，请勿回复<br>" +
                        "发送时间：%s" +
                        "</p>" +
                        "</div>" +
                        "</div>",
                typeName, typeName, code, expire, time
        );
    }
}
