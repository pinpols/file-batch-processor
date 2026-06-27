package com.example.filebatchprocessor.service.alert;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Webhook 渠道:平移原 BatchAlertEvaluator.notifyWebhook 的 payload,补 3s/5s 超时。 */
@Component
public class WebhookAlertSender implements AlertSender {

    private final boolean enabled;
    private final String url;
    private final RestClient restClient;

    // 生产构造器:兼容旧 key batch.alert.webhook.*
    @Autowired
    public WebhookAlertSender(
            @Value("${batch.alert.channels.webhook.enabled:${batch.alert.webhook.enabled:false}}") boolean enabled,
            @Value("${batch.alert.channels.webhook.url:${batch.alert.webhook.url:}}") String url) {
        this(enabled, url, RestClient.builder().requestFactory(timeoutFactory()));
    }

    // 测试用构造器:注入自定义 builder(可绑 MockRestServiceServer)
    public WebhookAlertSender(boolean enabled, String url, RestClient.Builder builder) {
        this.enabled = enabled;
        this.url = url;
        this.restClient = builder.build();
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        return f;
    }

    @Override
    public String channel() {
        return "webhook";
    }

    @Override
    public boolean isEnabled() {
        return enabled && url != null && !url.isBlank();
    }

    @Override
    public void send(AlertEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("alertCode", event.alertCode());
        payload.put("message", event.message());
        payload.put("service", event.source());
        payload.put("timestamp", event.timestamp().toString());
        payload.put("data", event.data());
        restClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
