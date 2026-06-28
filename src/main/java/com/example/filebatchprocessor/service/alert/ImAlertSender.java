package com.example.filebatchprocessor.service.alert;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** IM 渠道:飞书自定义机器人(text 消息)。报文 {"msg_type":"text","content":{"text":...}}。 */
@Component
public class ImAlertSender implements AlertSender {

    private final boolean enabled;
    private final String url;
    private final RestClient restClient;

    public ImAlertSender(
            @Value("${batch.alert.channels.im.enabled:false}") boolean enabled,
            @Value("${batch.alert.channels.im.url:}") String url,
            RestClient.Builder builder) {
        this.enabled = enabled;
        this.url = url;
        this.restClient = builder.build();
    }

    @Override
    public String channel() {
        return "im";
    }

    @Override
    public boolean isEnabled() {
        return enabled && url != null && !url.isBlank();
    }

    @Override
    public void send(AlertEvent event) {
        String text = "[" + event.severity() + "] " + event.alertCode() + ": " + event.message();
        Map<String, Object> body = Map.of("msg_type", "text", "content", Map.of("text", text));
        restClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
