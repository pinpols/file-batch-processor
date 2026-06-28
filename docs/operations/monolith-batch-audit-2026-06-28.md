# 单体批量项目全方位审核报告

日期: 2026-06-28  
范围: `file-batch-processor` 单体批量项目主干代码、配置、数据库迁移、运维接口、调度链路、文件导入/导出/分发、CI 安全门禁。  
方法: 借鉴分布式批量项目 BFS 审核经验,从入口层开始按广度优先覆盖“运维 API -> 调度/编排 -> Job 参数 -> 文件 IO -> 外部分发 -> DB 幂等 -> 观测/告警 -> CI/安全门禁”,再对高风险链路做纵深复查。

## 修复状态更新

更新时间: 2026-06-28 19:10 CST

本轮已按单体项目边界完成修复:收紧 `/ops/**` mutating 权限、生产 IO base-dir fail-fast、对账路径和哈希一致性、调度慢执行配置语义、临时明文权限与清扫、限流/熔断 requeue 忙轮询、leader 配置兼容、分发协议超时、归档依赖检查、SSRF 默认 fail-closed、下载授权去物理路径、旧上传服务路径穿越防护。

`allowParallel/dynamicShardMax` 未升级成真正并行执行,而是按单体稳态收口为“顺序分片 + 明确契约”:当前同步 `JobLauncher` 和共享批处理线程池下,强行并行 shard 会增加同 job 双跑、线程饥饿和状态聚合风险;后续若要真并行,应单独设计 bounded executor 与 shard 结果聚合。

本地已通过: unit profile、Spotless、SpotBugs、Flyway migration safety、关键新增单元测试。PostgreSQL 集成测试未跑到业务逻辑:本机 Docker daemon 未运行,local `localhost:5432` PostgreSQL 也不可达。

## 发现项

### P1: 部分会改变系统状态的运维端点只要求已登录,未要求 OPERATOR/ADMIN

证据:

- `src/main/java/com/example/filebatchprocessor/config/SecurityConfig.java:44-61` 只显式保护了部分 `POST /ops/tasks/*/toggle`、`POST /ops/change-requests`、`POST /ops/scheduler/trigger/*` 和迁移 POST,随后 `anyRequest().authenticated()`。
- `src/main/java/com/example/filebatchprocessor/controller/OpsFileDispatchController.java:29` 暴露 `POST /ops/file-dispatch/{taskId}/ack`。
- `src/main/java/com/example/filebatchprocessor/controller/OpsFileDispatchController.java:41` 暴露 `POST /ops/file-dispatch/{taskId}/resend`。
- `src/main/java/com/example/filebatchprocessor/controller/OpsFileController.java:169` 暴露 `POST /ops/files/{fileId}/reprocess`。
- `src/main/java/com/example/filebatchprocessor/controller/OpsBatchController.java:35`、`:76`、`:177` 暴露 rerun、compensate、retry 类操作。

影响:

低权限但已认证的账号可能触发补偿、重跑、重发、确认等生产操作。对批量系统来说,这类动作会改变幂等窗口、下游分发状态、对账结果和审计事实,风险高于普通只读查询。

建议:

- 将 `/ops/batch/**`、`/ops/file-dispatch/**`、`/ops/files/*/reprocess` 归入 `OPERATOR/ADMIN`。
- 对破坏性动作再细分为 `ADMIN` 或审批流。
- 增加 `SecurityConfig` 集成测试: `VIEWER` 对所有 mutating endpoint 必须 403。

### P1: 文件路径安全在默认配置下非 fail-closed,且对账作业绕过 PathSafety

证据:

- `src/main/resources/application.yml:99-102` 中 `batch.io.input-base-dir`、`output-base-dir`、`temp-dir` 默认均为空。
- `src/main/java/com/example/filebatchprocessor/util/PathSafety.java:25-60` 只有配置 baseDir 时才强制限制在基目录内;未配置时只拒绝包含 `..` 的路径。
- `src/main/java/com/example/filebatchprocessor/batch/config/FileImportJobConfig.java:104-111` 导入路径调用了 `PathSafety.confine(...)`。
- `src/main/java/com/example/filebatchprocessor/batch/config/DataExportJobConfig.java:133-146` 导出路径调用了 `PathSafety.confine(...)`。
- `src/main/java/com/example/filebatchprocessor/config/ReconcileJobConfig.java:137-193` 对账作业直接使用 `new FileSystemResource(inputFileName)` 和 `Files.newBufferedReader(...)`,没有同样的路径约束。

影响:

当运维 API、任务参数或数据库配置可提交 job 参数时,导入/导出/对账链路存在任意绝对路径读写的风险。即使生产配置了 baseDir,对账作业仍会绕过统一路径安全工具。

建议:

- 生产 profile 下要求 `BATCH_IO_INPUT_BASE_DIR` 和 `BATCH_IO_OUTPUT_BASE_DIR` 必填,空值直接启动失败。
- 将 `ReconcileJobConfig` 接入 `PathSafety.confine(inputBaseDir, inputFileName)`。
- 增加绝对路径、`..`、符号链接逃逸、对账作业参数的边界测试。

### P1: 调度超时不是硬超时,长耗时或卡死任务仍会占用调度线程和并发许可

证据:

- `src/main/java/com/example/filebatchprocessor/batch/scheduler/LaunchExecutor.java:198-208` 的 `runWithTimeout(...)` 先同步执行 `jobLauncher.run(...)`,结束后才比较耗时并记录 warn。
- `src/main/java/com/example/filebatchprocessor/config/BatchConfig.java:64-68` 名为 `asyncJobLauncher` 的 bean 实际直接返回同步 `JobLauncher`。
- `src/main/java/com/example/filebatchprocessor/batch/scheduler/TaskSchedulerService.java:110` 注入 `orchestration.scheduler.default-timeout-ms`。

影响:

配置项看起来提供了超时保护,但无法中断卡住的 Job。外部 HTTP/SFTP/FTP、数据库锁等待、大文件处理等一旦卡住,会持续占用线程、leader 调度能力、并发许可和任务状态。

建议:

- 如果当前语义只是观测,将配置和日志改名为 `warn-threshold-ms`。
- 如果需要真正超时,用可取消执行器 + `JobOperator.stop(...)` 或明确的软取消状态,并将状态写入业务运行表。
- 为外部分发和文件 IO 加入可控超时,避免调度层兜底失效。

### P2: 临时明文文件没有按设计实现 owner-only 权限和启动清扫

证据:

- 运维文档 `docs/operations/encrypted-compressed-intake.md` 要求临时明文使用专用目录、UUID 文件名、step 结束清理和启动清扫。
- `src/main/java/com/example/filebatchprocessor/batch/preprocess/TempFileManager.java:20-28` 仅创建目录并返回 UUID 文件名,未设置 POSIX 0700/0600 权限。
- `src/main/java/com/example/filebatchprocessor/batch/preprocess/FilePreprocessor.java:59`、`:67` 将解密/解压后的明文写入临时文件。
- `src/main/java/com/example/filebatchprocessor/batch/preprocess/TempFileManager.java:36` 有单文件删除,但未看到启动时清扫残留临时明文。

影响:

PGP 解密或压缩包解压后的明文可能受系统临时目录权限、umask 或 Windows ACL 影响。进程崩溃后残留文件无法保证被清理。

建议:

- POSIX 环境创建目录 `0700`,临时文件 `0600`;Windows 环境说明 ACL 策略或使用 owner-only API。
- 应用启动时清理本应用 temp-dir 下超龄 UUID 临时文件。
- 对异常路径、kill 后重启清扫、权限设置失败场景补测试。

### P2: 动态分片和 allowParallel 的实际执行仍是串行

证据:

- `src/main/java/com/example/filebatchprocessor/batch/scheduler/LaunchExecutor.java:76-80` 使用按 jobName 的单许可 `Semaphore`。
- `src/main/java/com/example/filebatchprocessor/batch/scheduler/LaunchExecutor.java:110-158` 每个 shard 都在循环内获取锁并同步执行。
- `src/main/java/com/example/filebatchprocessor/config/BatchConfig.java:64-68` launcher 实际同步。

影响:

配置层暴露了 `allowParallel`、`dynamicShardMax` 一类并行语义,但实际只是在参数上生成多 shard 后顺序执行。容量评估、SLA 估算和生产排障会被误导。

建议:

- 如果设计上就是顺序分片,改名和文档说明为 sequential shard。
- 如果要真正并行,引入 bounded executor,按任务/目标系统/作业名三层限流,并聚合 shard 结果。

### P2: 并发限流和熔断拒绝后的 requeue 可能形成忙轮询

证据:

- `src/main/java/com/example/filebatchprocessor/batch/scheduler/TaskSchedulerService.java:368-417` drain pass 已经对依赖 `WAITING` 做轮末暂存。
- `src/main/java/com/example/filebatchprocessor/batch/scheduler/TaskSchedulerService.java:445-454` 并发许可不足或 circuit breaker 拒绝时立即 `queueManager.requeue(task)`。
- `src/main/java/com/example/filebatchprocessor/batch/scheduler/QueueManager.java:32-33` `requeue` 只是立即 `queue.offer(task)`。

影响:

当高优先级任务持续被限流或熔断拒绝时,drain 线程可能在同一轮反复 poll/requeue 同一任务,造成 CPU 空转、指标噪声,并压制低优先级任务。

建议:

- 将限流/熔断拒绝也纳入本轮 hold list,由 drain tick 下一轮再重试。
- 或使用延迟队列/nextEligibleAt,保证重试间隔。
- 增加“许可不足时一次 drain 不重复处理同一任务”的单元测试。

### P2: Leader 配置 key 在 base 与代码之间不一致

证据:

- `src/main/resources/application.yml:255-259` 使用 `orchestration.scheduler.leader.lock-name`、`ttl-seconds`、`refresh-ms`。
- `src/main/java/com/example/filebatchprocessor/service/SchedulerLeaderService.java:28-34` 读取的是 `orchestration.scheduler.leader-lock-name`、`leader-ttl-seconds`、`force-leader`。
- `src/main/java/com/example/filebatchprocessor/service/SchedulerLeaderService.java:54` 读取 nested `leader.refresh-ms`。
- `src/main/resources/application-dev.yml:32-34` 又使用 flat key。

影响:

生产如果按 base 配置样式修改 `leader.lock-name` 或 `ttl-seconds`,服务可能继续使用默认值。当前默认值刚好一致,所以这个问题容易被隐藏,但集群部署时会影响 leader 锁隔离和租约。

建议:

- 用 `@ConfigurationProperties` 收敛成一个配置类。
- 保留旧 key alias 一段时间并记录启动告警。
- 增加配置绑定测试,覆盖 base、dev、prod 三种 key。

### P2: 外部分发 HTTP/SFTP/FTP 缺少显式连接和读写超时

证据:

- `src/main/java/com/example/filebatchprocessor/service/distribution/HttpFileDistributor.java:23` 构造 `HttpClient` 时没有 `connectTimeout`。
- `src/main/java/com/example/filebatchprocessor/service/distribution/HttpFileDistributor.java:69` 请求发送没有 per-request timeout。
- `src/main/java/com/example/filebatchprocessor/service/distribution/SftpFileDistributor.java:86-96` SSH connect/auth 没有显式超时。
- `src/main/java/com/example/filebatchprocessor/service/distribution/FtpFileDistributor.java:93-94` FTP 上传没有看到显式超时配置。

影响:

下游网络半开、目标不可达、认证卡顿时,分发任务可能长时间占用执行线程。结合“调度超时不是硬超时”,会放大生产阻塞面。

建议:

- 为 HTTP/SFTP/FTP 分别配置 connect/read/write/request timeout。
- timeout 进入统一失败分类,接入 retry、DLQ、告警和目标系统熔断。
- 将 timeout 值纳入运维可见配置。

### P2: 文件归档依赖检查字段已建模,实现仍是空桩

证据:

- `src/main/resources/db/migration/V1_33__file_alert_and_metrics.sql:130-135` 种子策略将 `check_dependency_before_archive` 设为 true。
- `src/main/java/com/example/filebatchprocessor/model/FileRetentionPolicy.java:30-33` 模型包含 `archiveBeforeDelete` 和 `checkDependencyBeforeArchive`。
- `src/main/java/com/example/filebatchprocessor/service/FileArchivalService.java:74` 调用 `hasActiveDependencies(file)`。
- `src/main/java/com/example/filebatchprocessor/service/FileArchivalService.java:112-114` `hasActiveDependencies(...)` 当前始终返回 false。

影响:

默认 dry-run 可以降低即时风险,但一旦开启真实归档/删除,代码会绕过策略表达出的依赖保护。批量系统中的文件可能仍被分发、对账、补偿或审计引用。

建议:

- 依赖检查至少覆盖 dispatch record、reception group、manifest、job instance、trace/diff/compensation 引用。
- 只有 `checkDependencyBeforeArchive=false` 或人工 override 时允许跳过。
- dry-run 报告中显示“本应因依赖阻止”的数量。

### P2: 分发目标 SSRF 防护默认 opt-in,只适合完全可信任务配置

证据:

- `src/main/resources/application.yml:287-290` `distribution.allowed-hosts` 默认空,`block-internal-targets` 默认 false。
- `src/main/java/com/example/filebatchprocessor/service/distribution/DistributionTargetValidator.java:68-77` allow-list 为空且未开启 internal block 时直接放行。

影响:

如果分发目标来自自助配置、上游系统或低权限运维输入,HTTP/FTP/SFTP 分发可能被用来访问内网、元数据地址或非预期目标。若目标只由受信任的管理员和迁移脚本维护,风险可接受但需明确部署假设。

建议:

- 生产默认配置 allow-list;如果允许内网分发,用显式域名后缀/主机清单表达。
- 对非管理员配置目标的入口强制走审批。
- 在启动日志中提示当前 SSRF 策略是开放还是受限。

### P3: 文件下载授权接口泄露服务端 storedPath

证据:

- `src/main/java/com/example/filebatchprocessor/controller/OpsFileController.java:195-215` `GET /ops/files/download/{fileId}/authorize` 返回 `storedPath`。
- `src/main/java/com/example/filebatchprocessor/config/SecurityConfig.java:60-61` 未单独约束该只读端点,任意已认证用户可访问。

影响:

当前接口只返回授权结果和元数据,不是直接下载文件。但泄露服务端路径会暴露部署结构,也会为后续实现真正下载时埋下权限边界混乱。

建议:

- 不向前端返回物理路径,改返回短期 download token 或 file id。
- 对下载授权按文件分类、角色、任务归属做权限校验。

### P3: 旧上传服务若被重新暴露会有路径穿越/覆盖风险

证据:

- `src/main/java/com/example/filebatchprocessor/service/FileProcessingService.java:34-44` 直接使用 `MultipartFile.getOriginalFilename()` 拼接 `uploadDirectory` 并 `transferTo(...)`。
- 当前未发现生产 controller 调用该 `saveFile(...)`,更像遗留服务。

影响:

如果未来被接到 controller,恶意文件名可能造成目录逃逸或覆盖已有文件。

建议:

- 未使用则删除。
- 如需保留,使用 `PathSafety`、文件名清洗、随机存储名、覆盖保护和内容类型校验。

## 现有防线与做得好的地方

- 导入主链路已经在 `FileImportJobConfig` 使用 `PathSafety`,导出主链路也在 `DataExportJobConfig` 使用同一工具,说明统一边界工具已存在,只需要补齐覆盖。
- 批量落库使用 `ON CONFLICT (business_key, batch_date, partition_key) DO NOTHING`,并由唯一约束兜底幂等。`FileImportRecordWriter` 还做了有界批内去重,避免全文件级去重导致 OOM。
- SFTP 主机密钥校验默认不是跳过模式;`application.yml` 中 `sftp.insecure-skip-host-key-check` 默认 false。
- 调度具备 leader 锁、执行去重、依赖等待、DLQ、熔断、并发限制、merge key 等分布式批量常见控制点,单体内已经保留了面向未来横向扩展的骨架。
- CI 已覆盖 compile、unit test、integration test、Spotless、SpotBugs、Trivy filesystem scan、gitleaks、CodeQL。OWASP Dependency Check 已不在当前主门禁中,避免了此前易卡顿的扫描项。

## BFS 复查视角

分布式批量项目的 BFS 审核经验可以迁移到本单体,但重点从“服务间一致性”调整为“单体内部边界一致性”:

1. 入口层: 先看 `/ops/**`、任务配置、Job 参数,确认谁能触发状态变化。
2. 编排层: 再看 scheduler、leader、dedup、dependency、concurrency、circuit breaker 是否语义一致。
3. Job 层: 看 import/export/reconcile 是否共享同一套路径、幂等、重试、超时、质量门。
4. IO 层: 看临时明文、归档删除、外部分发、下载授权是否存在越权或泄露。
5. 数据层: 看唯一键、迁移、状态机、归档依赖是否能支撑重跑和补偿。
6. 观测层: 看 metrics、日志、告警、dry-run 是否能定位生产事故。
7. 门禁层: 看 CI 静态扫描、密钥扫描、测试分层是否守住 PR 入口。

这次问题主要集中在“统一工具未全覆盖”和“配置语义与真实运行语义不一致”: 例如 PathSafety 已有但对账没接入;timeout 配置已有但不是硬超时;leader 配置已有但 key 不一致;归档依赖字段已有但实现为空。

## 修复路线建议

### 第一优先级: 合并前应修

- 收紧所有 mutating ops endpoint 的角色授权,补 `VIEWER` 403 测试。
- 让生产 profile 对 input/output base dir fail-closed,并把对账作业接入 PathSafety。
- 明确调度 timeout 语义:要么改名为 warn threshold,要么实现可取消超时。

### 第二优先级: 下一轮硬化

- 给临时明文目录/文件加 owner-only 权限和启动清扫。
- 限流/熔断 requeue 改成延迟或轮末暂存。
- 统一 leader 配置绑定。
- 分发协议补显式超时。
- 实现归档依赖检查。

### 第三优先级: 清理和产品化

- 分发目标 SSRF 策略按部署模式显式配置。
- 下载授权不返回物理路径。
- 删除或硬化旧 `FileProcessingService`。
- 梳理 `allowParallel` 与实际串行分片的命名/文档。

## 建议补充的测试清单

- `SecurityAndBoundaryIT`: viewer/operator/admin 对所有 `/ops/**` POST 权限矩阵。
- `PathSafetyIT`: import/export/reconcile 对绝对路径、`..`、symlink 的拒绝场景。
- `LaunchExecutorTest`: timeout 超过阈值时的实际状态语义;并行 shard 是否符合文档。
- `TaskSchedulerServiceTest`: concurrency/circuit requeue 不在同一 drain pass 内重复消费。
- `TempFileManagerTest`: POSIX 权限、异常后删除、启动清扫。
- `FileArchivalServiceTest`: 存在 dispatch/reception/manifest/job 引用时不得归档或删除。

## CI 与安全门禁结论

当前去掉 OWASP 后并不是“裸奔”: 仍有 SpotBugs、Trivy、gitleaks、CodeQL、单元测试和集成测试。OWASP Dependency Check 慢和误报高的问题可以接受删除,但建议保留依赖风险的替代机制:

- 保留 Trivy filesystem scan 作为依赖/CVE 快速门。
- Dependabot 或 Renovate 负责依赖更新提醒。
- 对生产发布可以单独加一个非阻塞 nightly dependency audit,不要卡普通 PR。

## 总结

这个单体批量项目的架构基础比普通 CRUD 单体更接近“批量平台”: 已经有调度、leader、幂等、DLQ、熔断、对账、分发和观测的骨架。主要短板不在缺组件,而在边界一致性: 一些关键组件已经存在,但没有覆盖到所有入口;一些配置项存在,但运行语义没有达到名称承诺。按上面的第一优先级修完后,项目的生产安全边界会明显收紧。
