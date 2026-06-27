package com.example.filebatchprocessor.unit.service.alert;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.example.filebatchprocessor.service.alert.AlertSeverity;
import com.example.filebatchprocessor.service.alert.ImAlertSender;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ImAlertSenderTest {

    @Test
    void sendsFeishuTextPayload() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://feishu.local/hook"))
                .andExpect(jsonPath("$.msg_type").value("text"))
                .andExpect(jsonPath("$.content.text").exists())
                .andRespond(withSuccess());

        ImAlertSender sender = new ImAlertSender(true, "http://feishu.local/hook", builder);
        sender.send(AlertEvent.of("CODE", AlertSeverity.CRITICAL, "msg", Map.of()));
        server.verify();
    }
}
