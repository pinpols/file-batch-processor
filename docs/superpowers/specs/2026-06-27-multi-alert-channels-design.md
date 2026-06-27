# 设计:多告警渠道(AlertSender SPI)

> 缺口 #7。日期 2026-06-27。状态:已批准设计,待写实现计划。

## 目标

把告警的"发送"抽象成可插拔 SPI,在不改"评估逻辑"的前提下支持 webhook(保留现有)+ email(SMTP)+ IM(飞书机器人)。严格单体单租户,不引消息队列/分布式。

## 现状(file:line)

- `BatchAlertEvaluator`(`service/BatchAlertEvaluator.java`):`notifyWebhook` 用 RestClient 直发,已带 3s/5s 超时,try/catch 吞异常;4 处触发点。已 leader 门控。
- `FileAlertService`(`service/FileAlertService.java`):当前只 `createAlert(...)` 落库 `FileAlertLog`(有 severity/alertCode/payload),**从不外发**。已 leader 门控。
- 配置 `application.yml` `batch.alert.*`(阈值 + webhook.enabled/url)。
- pom:有 starter-web(RestClient);**无 mail、无 greenmail**。

## 方案

`List<AlertSender>` SPI + `AlertDispatcher` 门面(收口失败隔离 + severity 门槛),各 sender `@Component` + `@ConditionalOnProperty` 守门。两个 Evaluator 只依赖 `AlertDispatcher`。

**否决备选**:纯 List 直注入 Evaluator(横切逻辑两处重复)、单 sender if-else 分发(违反开闭)。

## 范围边界

**做**:AlertSender SPI + AlertDispatcher;3 sender(Webhook 平移 / Email SMTP / IM 飞书机器人);配置迁到 `batch.alert.channels.*`(保留旧 webhook key 兼容别名);失败隔离 + 每渠道超时;`AlertEvent` 统一结构;File 链路按 severity 外发(WARNING 只落库、CRITICAL 才发)。

**不做(YAGNI)**:消息队列/总线;告警去重/分组/抑制/升级链;per-channel 路由规则引擎(v1 只全局 minSeverity + 各渠道 enabled);重试/异步线程池/持久化重发队列;模板引擎;短信/PagerDuty/多 IM 并存;不动 FileAlertLog 表与评估阈值。

## 组件/接口

```
service/alert/
  AlertEvent(record: source, alertCode, severity, title, message, Map data, timestamp)
  AlertSeverity(enum: INFO, WARNING, CRITICAL)
  AlertSender(接口: channel(), isEnabled(), send(AlertEvent) throws Exception)
  AlertDispatcher(@Component: 注入 List<AlertSender>; dispatch() 过 minSeverity + 逐 sender try/catch 隔离)
  WebhookAlertSender(平移现 notifyWebhook payload + 3s/5s RestClient)
  EmailAlertSender(JavaMailSender; @ConditionalOnProperty email.enabled)
  ImAlertSender(飞书自定义机器人, RestClient POST {msg_type:text}, 可选 HMAC 签名后置)
  (可选) AlertChannelProperties @ConfigurationProperties("batch.alert.channels")
```

配置:`batch.alert.min-severity`(默认 WARNING)+ `batch.alert.channels.{webhook,email,im}.*`;`spring.mail.*`(host/port/username/password 全 env 注入,smtp connectiontimeout/timeout 3s/5s)。

两入口改动:`BatchAlertEvaluator` 删 webhook 直发、4 处改 `dispatcher.dispatch(...)`(评估逻辑不动);`FileAlertService` 落库后追加 dispatch(severity 映射,best-effort 不参与事务回滚)。

## 文件清单

新增 `service/alert/` 6-7 个类。改:`BatchAlertEvaluator`、`FileAlertService`、`application.yml`、`pom.xml`(+starter-mail;test +greenmail)。

## 风险

1. 引 starter-mail 后即使 email 关闭也会自动配 JavaMailSender → EmailAlertSender 用 `@ConditionalOnProperty(email.enabled=true)` 守门,避免无 SMTP 环境(含测试上下文)报错。
2. 发送阻塞评估循环 → 每渠道必设超时;dispatcher 同步串行,渠道少可接受,不引线程池。
3. 敏感配置(SMTP 口令/IM secret)一律 env 注入,禁明文入仓、禁日志打印。
4. webhook 行为回归 → WebhookAlertSender 1:1 平移 payload + 超时;配置 key 迁移做兼容别名,避免升级静默丢告警。
5. File 链路噪音 → 按 minSeverity 过滤,WARNING 只落库。

## 测试计划

- 单测:AlertDispatcherTest(enabled/disabled、**一 sender 抛异常另一仍发**、severity 门槛);WebhookAlertSenderTest(MockRestServiceServer 验 payload 保真);ImAlertSenderTest(飞书报文);两 Evaluator mock dispatcher 验以正确 AlertEvent 调用。
- Email:引 `greenmail`(test scope)起内存 SMTP,EmailAlertSenderTest 验 subject/收件人/正文;退路是 mock JavaMailSender(覆盖更弱)。
- 不做:真 SMTP/真飞书集成测试(进 nightly/手动)。
