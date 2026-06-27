package com.example.filebatchprocessor.service.alert;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 告警门面:全局 min-severity 过滤 + 逐 sender 失败隔离。 */
@Slf4j
@Component
public class AlertDispatcher {

    private final List<AlertSender> senders;
    private final AlertSeverity minSeverity;

    public AlertDispatcher(
            List<AlertSender> senders, @Value("${batch.alert.min-severity:WARNING}") String minSeverity) {
        this.senders = senders;
        this.minSeverity = AlertSeverity.valueOf(minSeverity.trim().toUpperCase());
    }

    public void dispatch(AlertEvent event) {
        if (event.severity().ordinal() < minSeverity.ordinal()) {
            return;
        }
        for (AlertSender sender : senders) {
            if (!sender.isEnabled()) {
                continue;
            }
            try {
                sender.send(event);
            } catch (Exception e) {
                log.error("alert send failed: channel={}, code={}", sender.channel(), event.alertCode(), e);
            }
        }
    }
}
