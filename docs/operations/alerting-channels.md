# 多告警渠道运维文档

本系统支持三种告警外发渠道：**webhook**、**email**、**im（飞书自定义机器人）**。
告警由 `AlertDispatcher` 统一分发，批量链路（`BatchAlertEvaluator`）与文件链路（`FileAlertService`）共用同一套渠道。

## 渠道配置

各渠道在 `batch.alert.channels.{webhook,email,im}.*` 下配置，均带 `enabled` 开关：

| 渠道 | 关键配置 | 说明 |
| --- | --- | --- |
| webhook | `enabled` / `url` | 通用 HTTP webhook |
| email | `enabled` / `from` / `to` | SMTP 邮件，`to` 为收件人 |
| im | `enabled` / `url` | 飞书**自定义机器人 webhook 地址** |

> 飞书 `im.url` 填的是群机器人的自定义 webhook 地址（飞书后台「群设置 → 群机器人 → 自定义机器人」生成）。

## 严重级别与外发阈值

- **`batch.alert.min-severity`**（默认 `WARNING`）：全局外发底线。`AlertDispatcher` 先按此阈值过滤，低于该级别的告警**不外发任何渠道**。
- **`file.alert.externalize-min-severity`**（默认 `CRITICAL`）：File 链路（`FileAlertService`）的外发阈值。File 的 `WARNING` 级告警**只落库、不外发**，只有达到 `CRITICAL` 才进入 dispatcher 走渠道。

两者关系：File 告警先过 `externalize-min-severity` 决定是否外发，外发后再统一受 `batch.alert.min-severity` 全局底线约束。

## Email 渠道前置条件与守门

email 渠道除 `batch.alert.channels.email.*` 外，还需配置 SMTP：

```
spring.mail.host      = ${SMTP_HOST}
spring.mail.port      = ${SMTP_PORT:587}
spring.mail.username  = ${SMTP_USERNAME}
spring.mail.password  = ${SMTP_PASSWORD}
```

`EmailAlertSender` 用 `@ConditionalOnProperty(name="batch.alert.channels.email.enabled", havingValue="true")` 守门：
**关闭时该 Bean 完全不装配**，因此无 SMTP 环境（含测试上下文）时不会有任何副作用，也不需要 `spring.mail.host`。仅当开启 email 渠道时才需配齐上述 SMTP 参数。

> ⚠️ **email.enabled=true 时必须同时配置 `spring.mail.host`。** Spring Boot 的 `MailSenderAutoConfiguration` 仅在 `spring.mail.host` 非空时才创建 `JavaMailSender` bean。若开了 email 渠道但漏配 host，`EmailAlertSender` 通过 `ObjectProvider<JavaMailSender>` 解析得到 `null`，**该渠道自动禁用（`isEnabled()` 返回 false，不再让应用启动崩溃），但不会发信**，启动日志会打一条 `warn`（`email alert channel enabled but no JavaMailSender available; configure spring.mail.host`）。

> ⚠️ **生产环境务必保留这组 SMTP 超时**：email 的发送超时依赖 `spring.mail.properties.mail.smtp.connectiontimeout` / `timeout` / `writetimeout`（已在 `application.yml` 配为 3s/5s/5s）。一旦缺失，SMTP 半开/挂起连接会**阻塞 `@Scheduled` 告警评估单线程**，拖垮整条告警链路。

## 兼容旧配置

旧 key `batch.alert.webhook.{enabled,url}` 仍然有效：`WebhookAlertSender` 优先读 `batch.alert.channels.webhook.*`，缺省时**回落（fallback）**到旧的 `batch.alert.webhook.*`，无需改动历史配置即可平滑迁移。

## 失败隔离

`AlertDispatcher` 逐渠道 `try/catch`：

- 任一渠道发送异常被捕获并记录，**不影响其它渠道**，也**不阻塞告警评估循环**。
- 各渠道 sender（webhook/im）的 HTTP 客户端配置了连接/读取超时，email 走 SMTP 超时（`mail.smtp.connectiontimeout` / `timeout` / `writetimeout`），避免单渠道挂起拖垮整体。

## application.yml 配置片段示例

```yaml
batch:
  alert:
    min-severity: WARNING        # 全局外发底线
    channels:
      webhook:
        enabled: true
        url: "https://hooks.example.com/alert"
      email:
        enabled: true            # 开启后需配 spring.mail.*
        from: "alert@example.com"
        to: "ops@example.com"
      im:                        # 飞书自定义机器人
        enabled: true
        url: "https://open.feishu.cn/open-apis/bot/v2/hook/xxxx"
    # 兼容旧 key（仅作 webhook fallback）
    webhook:
      enabled: false
      url: ""

file:
  alert:
    externalize-min-severity: CRITICAL   # File 链路 WARNING 只落库

spring:
  mail:                          # 仅 email 渠道开启时需要
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
```
