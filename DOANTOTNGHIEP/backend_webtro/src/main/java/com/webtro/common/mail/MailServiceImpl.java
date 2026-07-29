package com.webtro.common.mail;

import com.webtro.config.properties.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Gửi email HTML bất đồng bộ ({@code @Async("mailTaskExecutor")}). Nếu {@code app.mail.enabled}
 * tắt (ví dụ khi chạy test), chỉ log thay vì gửi thật.
 *
 * <p>Lỗi gửi mail được nuốt (log ERROR) — mất một email thông báo không được làm hỏng thao tác
 * nghiệp vụ đã thành công (đăng ký, duyệt tin...). Đây là chủ ý.
 */
@Slf4j
@Service
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;

    public MailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine,
                           AppProperties appProperties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.appProperties = appProperties;
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendHtml(String to, String subject, String templateName, Map<String, Object> variables) {
        if (!appProperties.getMail().isEnabled()) {
            log.info("[MAIL tắt] Bỏ qua gửi tới {} - {}", to, subject);
            return;
        }
        try {
            Context context = new Context();
            if (variables != null) {
                variables.forEach(context::setVariable);
            }
            context.setVariable("frontendUrl", appProperties.getFrontend().getBaseUrl());
            String html = templateEngine.process("mail/" + templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(appProperties.getMail().getFromAddress(), appProperties.getMail().getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Đã gửi email tới {} - {}", to, subject);
        } catch (Exception e) {
            // Nuốt lỗi: email hỏng không được làm hỏng nghiệp vụ đã thành công.
            log.error("Gửi email tới {} thất bại ({}): {}", to, subject, e.getMessage());
        }
    }
}
