package com.example.filebatchprocessor.service.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email 渠道(SMTP)。@ConditionalOnProperty 守门:关闭时不装配,避免无 SMTP 环境(含测试上下文)
 * 因缺 JavaMailSender bean 报错。启用时需配置 spring.mail.host。
 */
@Component
@ConditionalOnProperty(name = "batch.alert.channels.email.enabled", havingValue = "true")
public class EmailAlertSender implements AlertSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String[] to;

    public EmailAlertSender(
            JavaMailSender mailSender,
            @Value("${batch.alert.channels.email.from:alert@example.com}") String from,
            @Value("${batch.alert.channels.email.to:}") String to) {
        this.mailSender = mailSender;
        this.from = from;
        this.to = (to == null || to.isBlank()) ? new String[0] : to.split("\\s*,\\s*");
    }

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public boolean isEnabled() {
        return to.length > 0;
    }

    @Override
    public void send(AlertEvent event) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("[" + event.severity() + "] " + event.alertCode());
        msg.setText(event.message() + "\n\ndata: " + event.data());
        mailSender.send(msg);
    }
}
