# 多告警渠道(#7)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把告警"发送"抽象成可插拔 `AlertSender` SPI(webhook/email/IM),由 `AlertDispatcher` 收口失败隔离与严重度门槛,两个评估器只改出口,评估逻辑零改。

**Architecture:** 新增 `service/alert/` 包:`AlertEvent`(数据)+`AlertSeverity`(枚举)+`AlertSender`(SPI)+`AlertDispatcher`(门面,注入 `List<AlertSender>`,逐个 try/catch 隔离 + 全局 min-severity 过滤)+ 3 个 sender。`BatchAlertEvaluator` 与 `FileAlertService` 改为依赖 `AlertDispatcher`。

**Tech Stack:** Spring Boot 4 / Java 21 / RestClient(webhook、飞书)/ spring-boot-starter-mail(JavaMailSender)/ GreenMail(测试 SMTP)。

**设计依据:** `docs/superpowers/specs/2026-06-27-multi-alert-channels-design.md`

**关键设计决策(实现前必读):**
- **严重度分层**:`AlertDispatcher` 有全局 `batch.alert.min-severity`(默认 `WARNING`,安全底线)。**Batch 链路**给每个 alertCode 映射合适严重度并全部 dispatch(保留现有"webhook 开启时 4 类都发"的行为)。**File 链路**额外有自己的外发阈值 `file.alert.externalize-min-severity`(默认 `CRITICAL`),只有达标的才 dispatch —— 实现 spec 的"File WARNING 只落库、CRITICAL 才外发",且不改 Batch 既有行为。
- **webhook 配置兼容**:新 key `batch.alert.channels.webhook.{enabled,url}`,用嵌套默认回落旧 key `batch.alert.webhook.{enabled,url}`(`@Value("${new:${old:default}}")`),升级不丢告警。
- **email 守门**:`EmailAlertSender` 加 `@ConditionalOnProperty(batch.alert.channels.email.enabled=true)`,关闭时不装配,避免无 SMTP 环境(含测试上下文)因缺 `JavaMailSender` bean 报错。

---

## File Structure

新增(`src/main/java/com/example/filebatchprocessor/service/alert/`):
- `AlertSeverity.java` — 枚举 INFO/WARNING/CRITICAL(ordinal 用于比较)
- `AlertEvent.java` — record 统一告警数据
- `AlertSender.java` — SPI 接口
- `AlertDispatcher.java` — 门面:失败隔离 + min-severity 过滤
- `WebhookAlertSender.java` — 平移现 notifyWebhook + 加 3s/5s 超时
- `ImAlertSender.java` — 飞书自定义机器人
- `EmailAlertSender.java` — SMTP,@ConditionalOnProperty 守门

修改:
- `service/BatchAlertEvaluator.java` — 删 webhook 直发,改用 dispatcher
- `service/FileAlertService.java` — createAlert 落库后按阈值 dispatch
- `src/main/resources/application.yml` — batch.alert.channels.* + min-severity + spring.mail.*
- `pom.xml` — +spring-boot-starter-mail(main)、+greenmail(test)

测试(`src/test/java/com/example/filebatchprocessor/unit/service/alert/`):
- `AlertDispatcherTest`、`WebhookAlertSenderTest`、`ImAlertSenderTest`、`EmailAlertSenderTest`
- 修改 `unit/service/BatchAlertEvaluatorTest`、`unit/service/FileAlertServiceTest`(若存在;无则新增最小验证)

---

## Task 1: AlertSeverity + AlertEvent + AlertSender(值类型与 SPI)

**Files:**
- Create: `src/main/java/com/example/filebatchprocessor/service/alert/AlertSeverity.java`
- Create: `src/main/java/com/example/filebatchprocessor/service/alert/AlertEvent.java`
- Create: `src/main/java/com/example/filebatchprocessor/service/alert/AlertSender.java`
- Test: `src/test/java/com/example/filebatchprocessor/unit/service/alert/AlertSeverityTest.java`

- [ ] **Step 1: 写失败测试(验证 severity 顺序,dispatcher 依赖它)**

```java
package com.example.filebatchprocessor.unit.service.alert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.service.alert.AlertSeverity;
import org.junit.jupiter.api.Test;

class AlertSeverityTest {
    @Test
    void ordinalOrderingInfoLtWarningLtCritical() {
        assertTrue(AlertSeverity.INFO.ordinal() < AlertSeverity.WARNING.ordinal());
        assertTrue(AlertSeverity.WARNING.ordinal() < AlertSeverity.CRITICAL.ordinal());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./mvnw test -Dtest=AlertSeverityTest -q`
Expected: 编译失败(AlertSeverity 不存在)。

- [ ] **Step 3: 创建三个类**

`AlertSeverity.java`:
```java
package com.example.filebatchprocessor.service.alert;

/** 告警严重度。ordinal 升序(INFO<WARNING<CRITICAL)用于 min-severity 过滤。 */
public enum AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}
```

`AlertEvent.java`:
```java
package com.example.filebatchprocessor.service.alert;

import java.time.LocalDateTime;
import java.util.Map;

/** 统一告警事件。各评估器构造它,sender 消费它。 */
public record AlertEvent(
        String source,
        String alertCode,
        AlertSeverity severity,
        String title,
        String message,
        Map<String, Object> data,
        LocalDateTime timestamp) {

    public static AlertEvent of(String alertCode, AlertSeverity severity, String message, Map<String, Object> data) {
        return new AlertEvent(
                "file-batch-processor", alertCode, severity, alertCode, message, data, LocalDateTime.now());
    }
}
```

`AlertSender.java`:
```java
package com.example.filebatchprocessor.service.alert;

/** 可插拔告警发送渠道。新增渠道 = 新增一个 @Component 实现。 */
public interface AlertSender {
    String channel();

    boolean isEnabled();

    /** 允许抛异常,由 AlertDispatcher 隔离,不影响其它渠道。 */
    void send(AlertEvent event) throws Exception;
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./mvnw test -Dtest=AlertSeverityTest -q`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/filebatchprocessor/service/alert src/test/java/com/example/filebatchprocessor/unit/service/alert/AlertSeverityTest.java
git commit -m "feat(alert): AlertSeverity/AlertEvent/AlertSender SPI 值类型"
```

---

## Task 2: AlertDispatcher(失败隔离 + min-severity 过滤)

**Files:**
- Create: `src/main/java/com/example/filebatchprocessor/service/alert/AlertDispatcher.java`
- Test: `src/test/java/com/example/filebatchprocessor/unit/service/alert/AlertDispatcherTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.example.filebatchprocessor.unit.service.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.service.alert.AlertDispatcher;
import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.example.filebatchprocessor.service.alert.AlertSender;
import com.example.filebatchprocessor.service.alert.AlertSeverity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AlertDispatcherTest {

    private static AlertSender sender(String name, boolean enabled, AtomicInteger counter, boolean throwOnSend) {
        return new AlertSender() {
            public String channel() {
                return name;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void send(AlertEvent event) {
                counter.incrementAndGet();
                if (throwOnSend) {
                    throw new RuntimeException("boom");
                }
            }
        };
    }

    private static AlertEvent critical() {
        return AlertEvent.of("CODE", AlertSeverity.CRITICAL, "msg", Map.of("k", "v"));
    }

    @Test
    void dispatchesToEnabledSkipsDisabled() {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        AlertDispatcher d = new AlertDispatcher(
                List.of(sender("a", true, a, false), sender("b", false, b, false)), "WARNING");
        d.dispatch(critical());
        assertEquals(1, a.get());
        assertEquals(0, b.get());
    }

    @Test
    void oneSenderThrowsOthersStillRun() {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        AlertDispatcher d = new AlertDispatcher(
                List.of(sender("a", true, a, true), sender("b", true, b, false)), "WARNING");
        d.dispatch(critical());
        assertEquals(1, a.get());
        assertEquals(1, b.get());
    }

    @Test
    void belowMinSeveritySuppressed() {
        AtomicInteger a = new AtomicInteger();
        AlertDispatcher d = new AlertDispatcher(List.of(sender("a", true, a, false)), "CRITICAL");
        d.dispatch(AlertEvent.of("CODE", AlertSeverity.WARNING, "msg", Map.of()));
        assertEquals(0, a.get());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./mvnw test -Dtest=AlertDispatcherTest -q`
Expected: 编译失败(AlertDispatcher 不存在)。

- [ ] **Step 3: 实现 AlertDispatcher**

```java
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
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./mvnw test -Dtest=AlertDispatcherTest -q`
Expected: 3 PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/.../service/alert/AlertDispatcher.java src/test/java/.../unit/service/alert/AlertDispatcherTest.java
git commit -m "feat(alert): AlertDispatcher 失败隔离 + min-severity 过滤"
```

---

## Task 3: WebhookAlertSender(平移 notifyWebhook + 加 3s/5s 超时)

**Files:**
- Create: `src/main/java/com/example/filebatchprocessor/service/alert/WebhookAlertSender.java`
- Test: `src/test/java/com/example/filebatchprocessor/unit/service/alert/WebhookAlertSenderTest.java`

- [ ] **Step 1: 写失败测试(用 MockRestServiceServer 验 payload 字段保真)**

```java
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
```

> 说明:为可测,WebhookAlertSender 提供一个包可见/测试用构造器接 `RestClient.Builder`;生产构造器内部用带超时的 builder。

- [ ] **Step 2: 跑测试确认失败**

Run: `./mvnw test -Dtest=WebhookAlertSenderTest -q`
Expected: 编译失败。

- [ ] **Step 3: 实现 WebhookAlertSender**

```java
package com.example.filebatchprocessor.service.alert;

import java.util.HashMap;
import java.util.Map;
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
    public WebhookAlertSender(
            @Value("${batch.alert.channels.webhook.enabled:${batch.alert.webhook.enabled:false}}") boolean enabled,
            @Value("${batch.alert.channels.webhook.url:${batch.alert.webhook.url:}}") String url) {
        this(enabled, url, RestClient.builder().requestFactory(timeoutFactory()));
    }

    // 测试用构造器:注入自定义 builder(可绑 MockRestServiceServer)
    WebhookAlertSender(boolean enabled, String url, RestClient.Builder builder) {
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
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./mvnw test -Dtest=WebhookAlertSenderTest -q`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/.../service/alert/WebhookAlertSender.java src/test/java/.../unit/service/alert/WebhookAlertSenderTest.java
git commit -m "feat(alert): WebhookAlertSender(平移 notifyWebhook + 超时)"
```

---

## Task 4: ImAlertSender(飞书自定义机器人)

**Files:**
- Create: `src/main/java/com/example/filebatchprocessor/service/alert/ImAlertSender.java`
- Test: `src/test/java/com/example/filebatchprocessor/unit/service/alert/ImAlertSenderTest.java`

- [ ] **Step 1: 写失败测试**

```java
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
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./mvnw test -Dtest=ImAlertSenderTest -q`
Expected: 编译失败。

- [ ] **Step 3: 实现 ImAlertSender**

```java
package com.example.filebatchprocessor.service.alert;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
            @Value("${batch.alert.channels.im.url:}") String url) {
        this(enabled, url, RestClient.builder().requestFactory(timeoutFactory()));
    }

    ImAlertSender(boolean enabled, String url, RestClient.Builder builder) {
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
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./mvnw test -Dtest=ImAlertSenderTest -q`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/.../service/alert/ImAlertSender.java src/test/java/.../unit/service/alert/ImAlertSenderTest.java
git commit -m "feat(alert): ImAlertSender 飞书自定义机器人"
```

---

## Task 5: EmailAlertSender + 依赖(starter-mail / greenmail)

**Files:**
- Modify: `pom.xml`(deps 区,line 40 `<dependencies>` 之后)
- Create: `src/main/java/com/example/filebatchprocessor/service/alert/EmailAlertSender.java`
- Test: `src/test/java/com/example/filebatchprocessor/unit/service/alert/EmailAlertSenderTest.java`

- [ ] **Step 1: 加依赖**

在 `pom.xml` 的 `<dependencies>` 区(参考现有 starter-web 在 line 48 附近)新增:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-mail</artifactId>
        </dependency>
        <dependency>
            <groupId>com.icegreen</groupId>
            <artifactId>greenmail-junit5</artifactId>
            <version>2.1.3</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: 写失败测试(GreenMail 内存 SMTP)**

```java
package com.example.filebatchprocessor.unit.service.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.example.filebatchprocessor.service.alert.AlertSeverity;
import com.example.filebatchprocessor.service.alert.EmailAlertSender;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailAlertSenderTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Test
    void sendsEmailToConfiguredRecipients() throws Exception {
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost("localhost");
        mail.setPort(greenMail.getSmtp().getPort());

        EmailAlertSender sender = new EmailAlertSender(mail, "alert@from.local", "ops@to.local");
        sender.send(AlertEvent.of("CODE", AlertSeverity.CRITICAL, "the message", Map.of("k", "v")));

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertEquals(1, received.length);
        assertTrue(received[0].getSubject().contains("CRITICAL"));
        assertTrue(received[0].getSubject().contains("CODE"));
        assertTrue(GreenMailUtil.getBody(received[0]).contains("the message"));
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `./mvnw test -Dtest=EmailAlertSenderTest -q`
Expected: 编译失败(EmailAlertSender 不存在)。

- [ ] **Step 4: 实现 EmailAlertSender**

```java
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
```

- [ ] **Step 5: 跑测试确认通过**

Run: `./mvnw test -Dtest=EmailAlertSenderTest -q`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add pom.xml src/main/java/.../service/alert/EmailAlertSender.java src/test/java/.../unit/service/alert/EmailAlertSenderTest.java
git commit -m "feat(alert): EmailAlertSender(SMTP, @ConditionalOnProperty) + starter-mail/greenmail"
```

---

## Task 6: BatchAlertEvaluator 改用 AlertDispatcher

**Files:**
- Modify: `src/main/java/com/example/filebatchprocessor/service/BatchAlertEvaluator.java`(全量改造,见下)
- Test: `src/test/java/com/example/filebatchprocessor/unit/service/BatchAlertEvaluatorDispatchTest.java`

- [ ] **Step 1: 写失败测试(mock dispatcher,验达阈值以正确 AlertEvent 调用)**

```java
package com.example.filebatchprocessor.unit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.service.BatchAlertEvaluator;
import com.example.filebatchprocessor.service.alert.AlertDispatcher;
import com.example.filebatchprocessor.service.alert.AlertEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BatchAlertEvaluatorDispatchTest {

    @Test
    void dispatchesWhenDlqBacklogExceedsThreshold() {
        BatchRunRecordRepository batchRepo = mock(BatchRunRecordRepository.class);
        DlqRecordRepository dlqRepo = mock(DlqRecordRepository.class);
        AlertDispatcher dispatcher = mock(AlertDispatcher.class);

        when(batchRepo.countByStatusAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(batchRepo.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(dlqRepo.countByHandledFalse()).thenReturn(9999L);
        when(dlqRepo.countByHandledFalseAndManualRequiredTrue()).thenReturn(0L);

        BatchAlertEvaluator evaluator = new BatchAlertEvaluator(batchRepo, dlqRepo, dispatcher);
        ReflectionTestUtils.setField(evaluator, "enabled", true);
        ReflectionTestUtils.setField(evaluator, "dlqBacklogThreshold", 100L);
        ReflectionTestUtils.setField(evaluator, "failureRateThreshold", 0.2);
        ReflectionTestUtils.setField(evaluator, "dlqManualThreshold", 20L);
        ReflectionTestUtils.setField(evaluator, "minThroughputRpsThreshold", 5.0);

        evaluator.evaluate();

        verify(dispatcher, atLeastOnce()).dispatch(any(AlertEvent.class));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./mvnw test -Dtest=BatchAlertEvaluatorDispatchTest -q`
Expected: 编译失败(BatchAlertEvaluator 构造器还是 2 参)。

- [ ] **Step 3: 改造 BatchAlertEvaluator**

把 `service/BatchAlertEvaluator.java` 整体替换为(删 webhook 字段/restClient/notifyWebhook,注入 dispatcher,4 处改 dispatch,按 alertCode 映射严重度):
```java
package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.service.alert.AlertDispatcher;
import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.example.filebatchprocessor.service.alert.AlertSeverity;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BatchAlertEvaluator {

    private final BatchRunRecordRepository batchRunRecordRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final AlertDispatcher alertDispatcher;

    @Value("${batch.alert.enabled:true}")
    private boolean enabled;

    @Value("${batch.alert.failure-rate-threshold:0.2}")
    private double failureRateThreshold;

    @Value("${batch.alert.dlq-backlog-threshold:100}")
    private long dlqBacklogThreshold;

    @Value("${batch.alert.dlq-manual-threshold:20}")
    private long dlqManualThreshold;

    @Value("${batch.alert.min-throughput-rps-threshold:5}")
    private double minThroughputRpsThreshold;

    public BatchAlertEvaluator(
            BatchRunRecordRepository batchRunRecordRepository,
            DlqRecordRepository dlqRecordRepository,
            AlertDispatcher alertDispatcher) {
        this.batchRunRecordRepository = batchRunRecordRepository;
        this.dlqRecordRepository = dlqRecordRepository;
        this.alertDispatcher = alertDispatcher;
    }

    @Scheduled(fixedDelayString = "${batch.alert.evaluate-ms:60000}")
    public void evaluate() {
        if (!enabled) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        long failures = batchRunRecordRepository.countByStatusAndCreatedAtAfter("FAILED", since);
        long completed = batchRunRecordRepository.countByStatusAndCreatedAtAfter("COMPLETED", since);
        long partial = batchRunRecordRepository.countByStatusAndCreatedAtAfter("PARTIAL", since);
        long total = failures + completed + partial;

        if (total > 0) {
            double failureRate = (double) failures / total;
            if (failureRate >= failureRateThreshold) {
                log.error("ALERT failure rate high: {} (threshold={})", failureRate, failureRateThreshold);
                alertDispatcher.dispatch(AlertEvent.of(
                        "BATCH_FAILURE_RATE_HIGH",
                        AlertSeverity.CRITICAL,
                        "Failure ratio > threshold",
                        Map.of("failureRate", failureRate, "threshold", failureRateThreshold, "windowMinutes", 15)));
            }
        }

        long backlog = dlqRecordRepository.countByHandledFalse();
        if (backlog >= dlqBacklogThreshold) {
            log.error("ALERT DLQ backlog high: {} (threshold={})", backlog, dlqBacklogThreshold);
            alertDispatcher.dispatch(AlertEvent.of(
                    "BATCH_DLQ_BACKLOG_HIGH",
                    AlertSeverity.WARNING,
                    "DLQ backlog exceeded threshold",
                    Map.of("backlog", backlog, "threshold", dlqBacklogThreshold)));
        }

        long manualBacklog = dlqRecordRepository.countByHandledFalseAndManualRequiredTrue();
        if (manualBacklog >= dlqManualThreshold) {
            log.error("ALERT DLQ manual backlog high: {} (threshold={})", manualBacklog, dlqManualThreshold);
            alertDispatcher.dispatch(AlertEvent.of(
                    "BATCH_DLQ_MANUAL_BACKLOG_HIGH",
                    AlertSeverity.CRITICAL,
                    "DLQ manual-required backlog exceeded threshold",
                    Map.of("manualBacklog", manualBacklog, "threshold", dlqManualThreshold)));
        }

        var recent = batchRunRecordRepository.findTop200ByOrderByCreatedAtDesc();
        double avgThroughput = recent.stream()
                .mapToDouble(v -> v.getThroughputRps() == null ? 0.0 : v.getThroughputRps())
                .average()
                .orElse(0.0);
        if (!recent.isEmpty() && avgThroughput < minThroughputRpsThreshold) {
            log.error("ALERT throughput degraded: {} rps (threshold={})", avgThroughput, minThroughputRpsThreshold);
            alertDispatcher.dispatch(AlertEvent.of(
                    "BATCH_THROUGHPUT_LOW",
                    AlertSeverity.WARNING,
                    "Average throughput below threshold",
                    Map.of("avgThroughputRps", avgThroughput, "threshold", minThroughputRpsThreshold)));
        }
    }
}
```

> 注意:`batch.alert.min-severity` 默认 WARNING,所以上面 4 类(含 WARNING)在 channel 开启时全部外发——保留现有"webhook 开启即收到全部"的行为。

- [ ] **Step 4: 跑测试确认通过 + 不破坏既有**

Run: `./mvnw test -Dtest=BatchAlertEvaluatorDispatchTest -q`
Expected: PASS。
再跑既有(若有):`./mvnw test -Dtest=BatchAlertEvaluator* -q` Expected: 全绿(旧 BatchAlertEvaluatorTest 若按旧 2 参构造会编译失败 → 一并更新为 3 参 + mock dispatcher)。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/.../service/BatchAlertEvaluator.java src/test/java/.../unit/service/BatchAlertEvaluatorDispatchTest.java
git commit -m "feat(alert): BatchAlertEvaluator 改用 AlertDispatcher(保留全量外发)"
```

---

## Task 7: FileAlertService 按阈值外发

**Files:**
- Modify: `src/main/java/com/example/filebatchprocessor/service/FileAlertService.java`(注入 dispatcher,createAlert 落库后按阈值 dispatch)
- Test: `src/test/java/com/example/filebatchprocessor/unit/service/FileAlertServiceDispatchTest.java`

- [ ] **Step 1: 写失败测试(CRITICAL 外发、WARNING 只落库)**

```java
package com.example.filebatchprocessor.unit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.model.FileAlertLog;
import com.example.filebatchprocessor.repository.FileAlertLogRepository;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.service.FileAlertService;
import com.example.filebatchprocessor.service.alert.AlertDispatcher;
import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FileAlertServiceDispatchTest {

    private FileAlertService newService(AlertDispatcher dispatcher) {
        FileAlertLogRepository alertRepo = mock(FileAlertLogRepository.class);
        when(alertRepo.save(any(FileAlertLog.class))).thenAnswer(i -> i.getArgument(0));
        FileAlertService svc = new FileAlertService(
                alertRepo,
                mock(FileAssetRecordRepository.class),
                mock(FileDispatchRecordRepository.class),
                new ObjectMapper(),
                dispatcher);
        ReflectionTestUtils.setField(svc, "fileExternalizeMinSeverity", "CRITICAL");
        return svc;
    }

    @Test
    void criticalIsExternalized() {
        AlertDispatcher dispatcher = mock(AlertDispatcher.class);
        FileAlertService svc = newService(dispatcher);
        svc.createAlert(null, "FILE_UNPROCESSED", "CRITICAL", "msg", Map.of());
        verify(dispatcher, times(1)).dispatch(any(AlertEvent.class));
    }

    @Test
    void warningIsNotExternalized() {
        AlertDispatcher dispatcher = mock(AlertDispatcher.class);
        FileAlertService svc = newService(dispatcher);
        svc.createAlert(null, "FILE_TIMEOUT", "WARNING", "msg", Map.of());
        verify(dispatcher, never()).dispatch(any(AlertEvent.class));
    }
}
```

> 注意:`createAlert` 第一个参数当前是某实体(看真实签名,Task 前先 Read `FileAlertService.createAlert` 的形参类型与顺序),测试传 null 即可。若 createAlert 内部解引用该实体,测试改传一个最小 mock。

- [ ] **Step 2: 跑测试确认失败**

Run: `./mvnw test -Dtest=FileAlertServiceDispatchTest -q`
Expected: 编译失败(构造器 4 参、无 fileExternalizeMinSeverity 字段)。

- [ ] **Step 3: 改造 FileAlertService**

- 构造器加第 5 参 `AlertDispatcher alertDispatcher` 并存字段。
- 加字段 `@Value("${file.alert.externalize-min-severity:CRITICAL}") private String fileExternalizeMinSeverity;`。
- 在 `createAlert(...)` 的 `return alertLogRepository.save(alert);` 之前,插入按阈值外发:

```java
        FileAlertLog saved = alertLogRepository.save(alert);
        try {
            AlertSeverity sev = AlertSeverity.valueOf(severity.trim().toUpperCase());
            AlertSeverity floor = AlertSeverity.valueOf(fileExternalizeMinSeverity.trim().toUpperCase());
            if (sev.ordinal() >= floor.ordinal()) {
                alertDispatcher.dispatch(AlertEvent.of(alertType, sev, message, payload == null ? Map.of() : payload));
            }
        } catch (Exception e) {
            log.error("file alert externalize failed: type={}", alertType, e);
        }
        return saved;
```

(import `com.example.filebatchprocessor.service.alert.*`;`alertType`/`severity`/`message`/`payload` 用 createAlert 实际形参名,Task 前 Read 确认。)

- [ ] **Step 4: 跑测试确认通过**

Run: `./mvnw test -Dtest=FileAlertServiceDispatchTest -q`
Expected: 2 PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/.../service/FileAlertService.java src/test/java/.../unit/service/FileAlertServiceDispatchTest.java
git commit -m "feat(alert): FileAlertService 按 externalize-min-severity 外发(WARNING 只落库)"
```

---

## Task 8: 配置迁移(application.yml)

**Files:**
- Modify: `src/main/resources/application.yml`(batch.alert 段 line 73-82;新增 spring.mail)

- [ ] **Step 1: 改 batch.alert 段**

把现有:
```yaml
  alert:
    enabled: true
    failure-rate-threshold: 0.2
    dlq-backlog-threshold: 100
    dlq-manual-threshold: 20
    min-throughput-rps-threshold: 5
    webhook:
      enabled: false
      url: ""
```
改为(保留旧 webhook 段作兼容,新增 channels + min-severity):
```yaml
  alert:
    enabled: true
    min-severity: WARNING            # 全局外发底线(INFO/WARNING/CRITICAL)
    failure-rate-threshold: 0.2
    dlq-backlog-threshold: 100
    dlq-manual-threshold: 20
    min-throughput-rps-threshold: 5
    channels:
      webhook:
        enabled: false
        url: ""
      email:
        enabled: false
        from: "alert@example.com"
        to: ""                       # 逗号分隔
      im:
        enabled: false               # 飞书自定义机器人
        url: ""
    # 兼容:旧 key 仍被 WebhookAlertSender 作 fallback 读取
    webhook:
      enabled: false
      url: ""
```

- [ ] **Step 2: 加 file.alert 外发阈值 + spring.mail**

在 `file.alert.*` 区(`FileAlertService` 读 file.alert.*)加:
```yaml
file:
  alert:
    externalize-min-severity: CRITICAL   # File 链路:WARNING 只落库,CRITICAL 才外发
```
在 `spring:` 顶层加(email 渠道用;全 env 注入,关闭时不影响):
```yaml
spring:
  mail:
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.connectiontimeout: 3000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000
```

- [ ] **Step 3: 校验 YAML**

Run: `python3 -c "import yaml; list(yaml.safe_load_all(open('src/main/resources/application.yml'))); print('OK')"`
Expected: OK。

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/application.yml
git commit -m "feat(alert): 配置迁移 batch.alert.channels.* + min-severity + spring.mail(兼容旧 webhook key)"
```

---

## Task 9: 全量回归 + 收尾

- [ ] **Step 1: 全 unit-test 不破坏既有 160**

Run: `./mvnw test -Punit-test 2>&1 | grep -E 'Tests run: [0-9]+, Failures.*Skipped: [0-9]+$|BUILD (SUCCESS|FAILURE)' | tail -2`
Expected: `Tests run: 160+N, Failures: 0, Errors: 0`(N=本次新增测试数),BUILD SUCCESS。
若旧 `BatchAlertEvaluatorTest`/`FileAlertServiceTest` 因构造器签名变更编译失败 → 更新其构造调用(补 mock AlertDispatcher / mock dispatcher 第 5 参),再跑绿。

- [ ] **Step 2: 编译 + (本机有 PG 时)集成回归**

Run: `./mvnw -DskipTests test-compile` Expected: BUILD SUCCESS。
若本机 PG 在线:`./mvnw test -Pintegration-test 2>&1 | tail -3` Expected: 全绿(告警相关 IT 若有则覆盖渠道装配)。

- [ ] **Step 3: 文档**

更新 `docs/user-guide/job-configuration-examples.md` 的告警配置段(或新增 `docs/operations/alerting-channels.md`),写明 channels.* 配置、min-severity 语义、email 需配 spring.mail.host、飞书 url 来源。提交。

---

## Self-Review 结论

- **Spec 覆盖**:AlertSender SPI(T1)/Dispatcher 失败隔离+severity(T2)/Webhook 平移+超时(T3)/IM 飞书(T4)/Email+守门+依赖(T5)/两评估器接入(T6,T7)/配置迁移+兼容别名(T8)/回归(T9)—— spec 各节均有对应 Task。
- **占位符**:无 TBD/TODO;每步含完整代码与命令。
- **类型一致**:`AlertEvent.of(code, severity, message, data)`、`AlertSender.send/channel/isEnabled`、`AlertDispatcher.dispatch`、各 sender 测试用构造器签名跨 Task 一致。
- **已知前置**:Task 7 前需 Read `FileAlertService.createAlert` 真实形参类型/顺序(第一参为某实体),按实调整测试与插入点。
