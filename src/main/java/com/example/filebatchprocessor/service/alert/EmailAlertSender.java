package com.example.filebatchprocessor.service.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email 渠道(SMTP)。@ConditionalOnProperty 守门:关闭时不装配,避免无 SMTP 环境(含测试上下文)
 * 因缺 JavaMailSender bean 报错。启用时需配置 spring.mail.host。
 *
 * <p>用 {@link ObjectProvider} 解析 JavaMailSender:即便 email.enabled=true 但未配 spring.mail.host
 * (MailSenderAutoConfiguration 不会创建 JavaMailSender),也不会抛 UnsatisfiedDependencyException
 * 把上下文搞崩,而是该渠道自身 disabled 并记 warn。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "batch.alert.channels.email.enabled", havingValue = "true")
public class EmailAlertSender implements AlertSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;
    private final String[] to;

    public EmailAlertSender(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${batch.alert.channels.email.from:alert@example.com}") String from,
            @Value("${batch.alert.channels.email.to:}") String to) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
        this.to = (to == null || to.isBlank()) ? new String[0] : to.split("\\s*,\\s*");
    }

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public boolean isEnabled() {
        return resolveMailSender() != null && to.length > 0;
    }

    @Override
    public void send(AlertEvent event) {
        JavaMailSender mailSender = resolveMailSender();
        if (mailSender == null) {
            log.warn("email alert channel enabled but no JavaMailSender available; configure spring.mail.host");
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("[" + event.severity() + "] " + event.alertCode());
        msg.setText(event.message() + "\n\ndata: " + event.data());
        mailSender.send(msg);
    }

    private JavaMailSender resolveMailSender() {
        if (mailSenderProvider == null) {
            return null;
        }
        try {
            return mailSenderProvider.getIfAvailable();
        } catch (RuntimeException ex) {
            log.warn("email alert channel disabled because JavaMailSender resolution failed", ex);
            return null;
        }
    }
}
