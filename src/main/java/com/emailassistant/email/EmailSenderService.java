package com.emailassistant.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * EmailSenderService — 真实邮件发送服务。
 *
 * <p>基于 Spring Boot 的 {@link JavaMailSender} 实现真实的邮件发送功能。
 * 配置信息在 application.yml 的 spring.mail.* 中设置。
 *
 * <p>支持：
 * <ul>
 *   <li>纯文本邮件</li>
 *   <li>HTML 邮件（可选）</li>
 * </ul>
 */
@Service
public class EmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender mailSender;

    public EmailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 发送纯文本邮件。
     *
     * @param to      收件人地址
     * @param subject 邮件主题
     * @param content 邮件正文（纯文本）
     * @return 发送结果描述
     */
    public String sendTextEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);

            log.info("邮件已发送至 {}，主题：{}", to, subject);
            return "✓ 邮件已成功发送至 " + to + "，主题：「" + subject + "」";

        } catch (Exception e) {
            log.error("邮件发送失败至 {}：{}", to, e.getMessage());
            return "✗ 邮件发送失败至 " + to + "，原因：" + e.getMessage();
        }
    }

    /**
     * 发送 HTML 格式邮件。
     *
     * @param to      收件人地址
     * @param subject 邮件主题
     * @param html    邮件正文（HTML）
     * @return 发送结果描述
     */
    public String sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);

            log.info("HTML 邮件已发送至 {}，主题：{}", to, subject);
            return "✓ HTML 邮件已成功发送至 " + to + "，主题：「" + subject + "」";

        } catch (MessagingException e) {
            log.error("HTML 邮件发送失败至 {}：{}", to, e.getMessage());
            return "✗ 邮件发送失败至 " + to + "，原因：" + e.getMessage();
        }
    }
}
