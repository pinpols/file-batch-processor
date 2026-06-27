package com.example.filebatchprocessor.unit.service.alert;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.example.filebatchprocessor.service.alert.AlertSeverity;
import com.example.filebatchprocessor.service.alert.WebhookAlertSender;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WebhookAlertSenderTest {

    @Test
    void sendsExpectedPayload() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hook.local/alert"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.alertCode").value("CODE"))
                .andExpect(jsonPath("$.service").value("file-batch-processor"))
                .andExpect(jsonPath("$.data.k").value("v"))
                .andRespond(withSuccess());

        WebhookAlertSender sender = new WebhookAlertSender(true, "http://hook.local/alert", builder);
        sender.send(AlertEvent.of("CODE", AlertSeverity.CRITICAL, "msg", Map.of("k", "v")));
        server.verify();
    }
}
