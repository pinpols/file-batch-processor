# 清单(manifest)驱动入库(#3+#6)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`).

**Goal:** 上游送一个 JSON 控制文件(manifest)列出本批期望文件 + 各文件期望条数/MD5;系统等这组文件到齐且对账通过才触发导入,缺文件/条数不符则告警挂起。灰度开关默认关,不破坏现有逐文件路径。

**Architecture:** 新表 `reception_group` + `reception_group_member`,`file_reception_queue` 加可空 `reception_group_id`。manifest 文件到达 → `FileReceptionService` 识别 → `ReceptionGroupService.registerFromManifest` 建组。轮询任务 `reception-group-monitor` 周期调 `ReceptionGroupCompletionService.evaluate`:required 成员全到齐 → `ManifestReconcileService` 对账(文件存在 + 实际条数 == 期望 + MD5)→ 通过则组 COMPLETE 并触发各成员导入,失败则 FAILED/EXPIRED + 告警。

**Tech Stack:** Java 21 / Spring Batch / JPA / Jackson(manifest JSON)/ PostgreSQL / Testcontainers(IT)。

**设计依据:** `docs/superpowers/specs/2026-06-27-manifest-driven-intake-design.md`

**已核实(main 分支):**
- `FileReceptionService`:`receiveFile(fileName, filePath, sourceSystem)`、`calculateHash(filePath)` 用 **MD5**、`findPendingFiles()`、markAs* 状态方法。
- `FileReceptionQueue`(@Table file_reception_queue):id、file_name(唯一 uk_file_name_received)、file_path、file_size、file_hash(128)、status(默认 RECEIVED)、source_system、created_at。
- `file_alert_log` 的 alert_type CHECK 在 `V1_33__file_alert_and_metrics.sql`。
- **main 上最新迁移 V1_35**;本计划用 **V1_38**(避开审计 PR 的 V1_36/37)。
- 任务调度:`task_definition`+`task_trigger` seed(V1_0 风格),job_name → `@Bean("...")`。`OperationalTaskJobConfig.fileReceptionTasklet` 是现成的轮询 tasklet 模式参考。

**关键设计决策:**
- manifest = JSON;到达靠固定后缀 `.manifest.json`。
- 对账复用接收链路已算的 MD5(`file_reception_queue.file_hash`),零重算;manifest 声明非 MD5 → fail-fast。
- 组状态机:WAITING_FILES → COMPLETE → DISPATCHED / EXPIRED(挂起,不删)/ FAILED。
- 灰度:`batch.file.reception.group.enabled` 默认 false;非组文件 `reception_group_id` 为 NULL,走原路径。

---

## File Structure

新增:
- `db/migration/V1_38__reception_group_and_manifest.sql`
- `model/ReceptionGroup.java`、`ReceptionGroupMember.java`、`ReceptionGroupStatus.java`(enum)
- `repository/ReceptionGroupRepository.java`、`ReceptionGroupMemberRepository.java`
- `manifest/ParsedManifest.java`(DTO,record)、`ManifestParser.java`(接口)、`JsonManifestParser.java`
- `service/ReceptionGroupService.java`、`ManifestReconcileService.java`、`ReceptionGroupCompletionService.java`
- `batch/config/OperationalTaskJobConfig.java`(改:加 receptionGroupJob)
- `service/FileReceptionService.java`(改:manifest 识别钩子)

测试:`unit/manifest/JsonManifestParserTest`、IT `integration/ManifestDrivenIntakeIT`(Testcontainers)。

---

## Task 1: Flyway V1_38(表 + 列 + CHECK + seed task)

**Files:** Create `src/main/resources/db/migration/V1_38__reception_group_and_manifest.sql`

- [ ] **Step 1: 写迁移**
```sql
-- 清单驱动入库:到达组 + 成员 + 控制文件对账(#3+#6)

CREATE TABLE IF NOT EXISTS reception_group (
    id BIGSERIAL PRIMARY KEY,
    manifest_id VARCHAR(200) NOT NULL UNIQUE,
    source_system VARCHAR(100),
    biz_date VARCHAR(32),
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING_FILES',
    total_members INTEGER NOT NULL DEFAULT 0,
    arrived_members INTEGER NOT NULL DEFAULT 0,
    deadline TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_reception_group_status ON reception_group(status);

CREATE TABLE IF NOT EXISTS reception_group_member (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES reception_group(id),
    expected_file_name VARCHAR(500) NOT NULL,
    expected_record_count BIGINT,
    expected_checksum VARCHAR(128),
    checksum_algorithm VARCHAR(20) DEFAULT 'MD5',
    required BOOLEAN NOT NULL DEFAULT TRUE,
    actual_queue_id BIGINT,
    actual_record_count BIGINT,
    reconcile_status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_group_member_file UNIQUE (group_id, expected_file_name)
);
CREATE INDEX IF NOT EXISTS idx_group_member_group ON reception_group_member(group_id);

ALTER TABLE file_reception_queue ADD COLUMN IF NOT EXISTS reception_group_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_file_reception_group ON file_reception_queue(reception_group_id);

-- seed reception-group-monitor task(默认禁用,运维开启;FIXED_RATE 2 分钟)
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('reception-group-monitor', 'receptionGroupJob', '清单到达组监控:每 2 分钟检查组是否到齐并对账', 'NORMAL', TRUE, FALSE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
SELECT 'reception-group-monitor', 'FIXED_RATE', 120000, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger WHERE task_id = 'reception-group-monitor' AND trigger_type = 'FIXED_RATE');
```

> 注:`file_alert_log` 的 alert_type CHECK 扩展(加 GROUP_INCOMPLETE/GROUP_RECONCILE_FAIL)——实施者先 Read `V1_33__file_alert_and_metrics.sql` 看 CHECK 的真实写法(是 `CHECK (alert_type IN (...))` 还是 DO $$ 块),在 V1_38 末尾用 `ALTER TABLE file_alert_log DROP CONSTRAINT <名> ; ADD CONSTRAINT <名> CHECK (alert_type IN (...原有... ,'GROUP_INCOMPLETE','GROUP_RECONCILE_FAIL'))`。若 CHECK 用的是不易增量改的形式,改用 `... ADD CONSTRAINT ck_file_alert_group CHECK (...)` 仅约束新类型,或评估后放宽(把约束名/列名按 V1_33 实际填)。**先 Read V1_33 再定写法**,确保 squawk/Flyway 不报错。

- [ ] **Step 2: 验证迁移语法**(本机有 PG 时)`./mvnw -q -DskipTests test-compile`(编译不依赖迁移,但迁移在 IT 跑);或本地 psql dry-run。提交后由 Task 8 的 IT 实际跑 Flyway 验证。

- [ ] **Step 3: 提交**
```bash
git add src/main/resources/db/migration/V1_38__reception_group_and_manifest.sql
git commit -m "feat(intake): V1_38 reception_group/member + queue 列 + monitor task seed"
```

---

## Task 2: 实体 + 枚举 + 仓库

**Files:**
- Create: `model/ReceptionGroupStatus.java`、`ReceptionGroup.java`、`ReceptionGroupMember.java`、`repository/ReceptionGroupRepository.java`、`ReceptionGroupMemberRepository.java`
- Test: `unit/model/ReceptionGroupStatusTest.java`(简单枚举存在性测试)

> 实施者:参考现有 `model/FileReceptionQueue.java` 的 JPA 注解风格(@Entity/@Table/@Column/@Id @GeneratedValue)。

- [ ] **Step 1: 写枚举存在性测试**
```java
package com.example.filebatchprocessor.unit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.model.ReceptionGroupStatus;
import org.junit.jupiter.api.Test;

class ReceptionGroupStatusTest {
    @Test
    void hasExpectedStates() {
        assertEquals(5, ReceptionGroupStatus.values().length);
        ReceptionGroupStatus.valueOf("WAITING_FILES");
        ReceptionGroupStatus.valueOf("COMPLETE");
        ReceptionGroupStatus.valueOf("DISPATCHED");
        ReceptionGroupStatus.valueOf("EXPIRED");
        ReceptionGroupStatus.valueOf("FAILED");
    }
}
```

- [ ] **Step 2:** `./mvnw test -Dtest=ReceptionGroupStatusTest -q` 红。

- [ ] **Step 3: 实现**

`ReceptionGroupStatus.java`:
```java
package com.example.filebatchprocessor.model;

public enum ReceptionGroupStatus {
    WAITING_FILES,
    COMPLETE,
    DISPATCHED,
    EXPIRED,
    FAILED
}
```

`ReceptionGroup.java`(@Entity @Table(name="reception_group"),字段对齐 V1_38:id、manifestId、sourceSystem、bizDate、status[String]、totalMembers、arrivedMembers、deadline、createdAt、updatedAt;getter/setter;status 默认 "WAITING_FILES")。

`ReceptionGroupMember.java`(@Entity @Table(name="reception_group_member"),字段:id、groupId、expectedFileName、expectedRecordCount[Long]、expectedChecksum、checksumAlgorithm、required[boolean]、actualQueueId[Long]、actualRecordCount[Long]、reconcileStatus、createdAt;getter/setter)。

`ReceptionGroupRepository extends JpaRepository<ReceptionGroup, Long>`:`Optional<ReceptionGroup> findByManifestId(String)`、`List<ReceptionGroup> findByStatus(String)`。
`ReceptionGroupMemberRepository extends JpaRepository<ReceptionGroupMember, Long>`:`List<ReceptionGroupMember> findByGroupId(Long)`、`Optional<ReceptionGroupMember> findByGroupIdAndExpectedFileName(Long, String)`。

- [ ] **Step 4:** `./mvnw test -Dtest=ReceptionGroupStatusTest -q` 绿;`./mvnw -DskipTests test-compile` SUCCESS。
- [ ] **Step 5: 提交** `git add` 这些文件 + 测试;`git commit -m "feat(intake): ReceptionGroup/Member 实体 + 仓库"`

---

## Task 3: ManifestParser(JSON)

**Files:**
- Create: `manifest/ParsedManifest.java`、`manifest/ManifestParser.java`、`manifest/JsonManifestParser.java`
- Test: `unit/manifest/JsonManifestParserTest.java`

- [ ] **Step 1: 写失败测试**
```java
package com.example.filebatchprocessor.unit.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.manifest.JsonManifestParser;
import com.example.filebatchprocessor.manifest.ParsedManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonManifestParserTest {

    private final JsonManifestParser parser = new JsonManifestParser(new ObjectMapper());

    @Test
    void parsesValidManifest() {
        String json = """
            {"manifestId":"M1","sourceSystem":"S","bizDate":"2026-06-27",
             "files":[
               {"fileName":"a.csv","expectedRecordCount":10,"checksum":"abc","required":true},
               {"fileName":"b.csv"}
             ]}""";
        ParsedManifest m = parser.parse(json);
        assertEquals("M1", m.manifestId());
        assertEquals(2, m.files().size());
        assertEquals("a.csv", m.files().get(0).fileName());
        assertEquals(10L, m.files().get(0).expectedRecordCount());
        assertTrue(m.files().get(1).required()); // 默认 required=true
    }

    @Test
    void rejectsMissingManifestId() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("{\"files\":[{\"fileName\":\"a.csv\"}]}"));
    }

    @Test
    void rejectsNonMd5ChecksumAlgorithm() {
        String json = "{\"manifestId\":\"M\",\"files\":[{\"fileName\":\"a\",\"checksum\":\"x\",\"checksumAlgorithm\":\"SHA-256\"}]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }
}
```

- [ ] **Step 2:** 红。

- [ ] **Step 3: 实现**

`ParsedManifest.java`:
```java
package com.example.filebatchprocessor.manifest;

import java.util.List;

public record ParsedManifest(String manifestId, String sourceSystem, String bizDate, List<ExpectedFile> files) {
    public record ExpectedFile(
            String fileName, Long expectedRecordCount, String checksum, String checksumAlgorithm, boolean required) {}
}
```

`ManifestParser.java`:
```java
package com.example.filebatchprocessor.manifest;

public interface ManifestParser {
    ParsedManifest parse(String content);
}
```

`JsonManifestParser.java`(@Component;用 ObjectMapper 读 JsonNode,校验 manifestId 非空、files 非空、每个 file fileName 非空、checksumAlgorithm 若给且非 MD5 则抛;required 缺省 true;checksumAlgorithm 缺省 MD5):
```java
package com.example.filebatchprocessor.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JsonManifestParser implements ManifestParser {

    private final ObjectMapper objectMapper;

    public JsonManifestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedManifest parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            String manifestId = text(root, "manifestId");
            if (manifestId == null || manifestId.isBlank()) {
                throw new IllegalArgumentException("manifest missing manifestId");
            }
            JsonNode filesNode = root.get("files");
            if (filesNode == null || !filesNode.isArray() || filesNode.isEmpty()) {
                throw new IllegalArgumentException("manifest has no files");
            }
            List<ParsedManifest.ExpectedFile> files = new ArrayList<>();
            for (JsonNode f : filesNode) {
                String fileName = text(f, "fileName");
                if (fileName == null || fileName.isBlank()) {
                    throw new IllegalArgumentException("manifest file missing fileName");
                }
                Long count = f.hasNonNull("expectedRecordCount") ? f.get("expectedRecordCount").asLong() : null;
                String checksum = text(f, "checksum");
                String algo = f.hasNonNull("checksumAlgorithm") ? f.get("checksumAlgorithm").asText() : "MD5";
                if (!"MD5".equalsIgnoreCase(algo)) {
                    throw new IllegalArgumentException("only MD5 checksum supported, got: " + algo);
                }
                boolean required = !f.hasNonNull("required") || f.get("required").asBoolean();
                files.add(new ParsedManifest.ExpectedFile(fileName, count, checksum, "MD5", required));
            }
            return new ParsedManifest(manifestId, text(root, "sourceSystem"), text(root, "bizDate"), files);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid manifest JSON", e);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
```

- [ ] **Step 4:** 绿。**Step 5:** 提交 `feat(intake): JsonManifestParser`。

---

## Task 4: ReceptionGroupService(建组 + 绑定到达文件)

**Files:** Create `service/ReceptionGroupService.java`;Test `unit/service/ReceptionGroupServiceTest.java`(mock repos)。

- [ ] **Step 1: 测试**(mock ReceptionGroupRepository/MemberRepository/FileReceptionQueueRepository):
  - `registerFromManifest(parsedManifest)`:幂等——manifestId 已存在则不重建(findByManifestId 返回非空时不 save 新组);新建时 save group(status WAITING_FILES、totalMembers=files.size、deadline=now+ttl)+ 每个 file save member。
  - `bindArrivedFile(groupId, queueRow)`:按 fileName 找 member,set actualQueueId + actualRecordCount(可空)+ group.arrivedMembers++。
  写 2 个用例:`registerCreatesGroupAndMembers`(stub findByManifestId 返回 empty → verify saveAll/ save 调用、totalMembers 正确)、`registerIsIdempotent`(findByManifestId 返回 Optional.of(existing) → verify 不 save 新组)。

- [ ] **Step 2-5:** 实现 `ReceptionGroupService`(@Service,注入三个 repo + `@Value("${batch.file.reception.group.ttl-minutes:360}") long ttlMinutes`):
  - `registerFromManifest(ParsedManifest m)`:`if (repo.findByManifestId(m.manifestId()).isPresent()) { log + return existing; }` 否则建 group(set 字段)、save,遍历 files 建 member(groupId=group.id)save;回扫已到达未绑定的同名 queue 行(`fileReceptionQueueRepository.findByFileName(name)` 若有则 bind)。
  - `bindArrivedFile(Long groupId, FileReceptionQueue row)`:member = memberRepo.findByGroupIdAndExpectedFileName;set actualQueueId/actualRecordCount;row.setReceptionGroupId(groupId) save;group.arrivedMembers++ save。
  TDD 绿后提交 `feat(intake): ReceptionGroupService 建组+绑定`。

> 注:FileReceptionQueueRepository 需有 `findByFileName(String)`/`Optional findByFileName`——Read 该 repo,若无则在本任务补一个派生查询方法。

---

## Task 5: ManifestReconcileService(对账)

**Files:** Create `service/ManifestReconcileService.java`;Test `unit/service/ManifestReconcileServiceTest.java`。

- [ ] 逻辑:`ReconcileResult reconcile(ReceptionGroupMember member, FileReceptionQueue queueRow)`:
  - 文件存在性:queueRow 非空(已到达);
  - 条数:member.expectedRecordCount 非空时,实际条数(countDataLines:读文件数行跳表头/空行,或用 queueRow 已有的计数若有)== expected;
  - MD5:member.expectedChecksum 非空时,queueRow.fileHash(接收已算 MD5)== expectedChecksum;
  - 返回 `ReconcileResult(boolean pass, List<String> mismatches)`。
  测试:全通过 / 条数不符 / checksum 不符 三例(用临时文件 + 设 queueRow.fileHash)。countDataLines 参考现有 `ReconcileJobConfig` 的数行逻辑(Read 它);若复杂,本服务自己读文件数非空行减表头。
- [ ] TDD 绿后提交 `feat(intake): ManifestReconcileService 对账`。

---

## Task 6: ReceptionGroupCompletionService(到齐判定 + 触发)

**Files:** Create `service/ReceptionGroupCompletionService.java`;Test `unit/service/ReceptionGroupCompletionServiceTest.java`。

- [ ] 逻辑:`evaluate(Long groupId)`:
  - 取 group + members;若所有 **required** member 都已绑定(actualQueueId 非空)→ 进对账:对每个 member 调 ManifestReconcileService.reconcile;
    - 全 pass → group.status=COMPLETE,save;(触发导入 = 调用现有按文件导入入口;v1 可只置 COMPLETE→DISPATCHED 并打日志/留 TODO 钩子,真正触发用既有 task 机制——为可测,触发逻辑抽成一个可注入的 `Consumer`/方法,测试 verify 被调);member.reconcileStatus=PASS。
    - 任一 fail → group.status=FAILED,写 file_alert_log(GROUP_RECONCILE_FAIL,经 FileAlertService.createAlert),记 mismatch。
  - required 未到齐 → 检查 deadline:超期 → group.status=EXPIRED + 告警(GROUP_INCOMPLETE);未超期 → 保持 WAITING_FILES。
  测试(mock):全到齐+对账过→COMPLETE+触发被调;缺 required+超 deadline→EXPIRED+告警;到齐但对账 fail→FAILED+告警。
- [ ] TDD 绿后提交 `feat(intake): ReceptionGroupCompletionService 到齐判定+对账+触发`。

---

## Task 7: receptionGroupJob + FileReceptionService manifest 钩子 + 配置

**Files:** Modify `batch/config/OperationalTaskJobConfig.java`、`service/FileReceptionService.java`、`application.yml`。

- [ ] **OperationalTaskJobConfig**:照搬 `fileReceptionTasklet` 模式新增 `receptionGroupTasklet`(注入 ReceptionGroupRepository + ReceptionGroupCompletionService;leader 门控可选)——扫 status=WAITING_FILES 的组,对每个调 `completionService.evaluate(group.id)`;`receptionGroupStep` + `@Bean("receptionGroupJob")`。
- [ ] **FileReceptionService.receiveFile**:开头识别 manifest——若 fileName 以 `batch.file.reception.group.manifest-suffix`(默认 `.manifest.json`)结尾,则读文件内容 → ManifestParser.parse → ReceptionGroupService.registerFromManifest,**return 一个标记/不进入普通文件处理**(manifest 本身不当数据文件);否则走原有逻辑,并在普通文件入队后,若该文件名命中某 WAITING_FILES 组成员则 bindArrivedFile(可在 monitor 里做绑定,简化 receiveFile)。为降耦合,v1:receiveFile 只识别 manifest;数据文件绑定放在 receptionGroupTasklet 里(evaluate 前先扫未绑定的到达文件按名 bind)。注入 ManifestParser + ReceptionGroupService(注意构造器变更影响既有测试 → 补 mock)。
- [ ] **application.yml**(并入 batch.file.reception):
```yaml
batch:
  file:
    reception:
      group:
        enabled: false
        manifest-suffix: .manifest.json
        poll-rate-ms: 120000
        ttl-minutes: 360
```
- [ ] 编译 + 相关单测绿(FileReceptionService 既有测试若因构造器变更编译失败,补 mock)。提交 `feat(intake): receptionGroupJob + manifest 识别 + 配置`。

---

## Task 8: IT(Testcontainers)+ 文档

**Files:** Test `integration/ManifestDrivenIntakeIT.java`(Testcontainers PG,参考现有 IT 风格如 ReconcileJobConfigIT);文档。

- [ ] **IT 三场景**(用真 PG + Flyway 跑 V1_38):
  1. 齐且对账通过:registerFromManifest(2 required 文件,带 count+MD5)→ 两文件 receiveFile(写真文件 + 让 fileHash 对上)→ 跑 receptionGroupTasklet/evaluate → 断言 group COMPLETE/DISPATCHED、member reconcile PASS、触发被调。
  2. 缺文件:只到 1/2 → evaluate → WAITING_FILES、arrived=1;推进过 ttl(可直接 set deadline 过去)再 evaluate → EXPIRED + file_alert_log 有 GROUP_INCOMPLETE。
  3. 条数/MD5 不符 → FAILED + GROUP_RECONCILE_FAIL,不触发。
- [ ] **全量回归**:`./mvnw test -Punit-test`(0 失败);本机有 PG 则 `./mvnw test -Pintegration-test`(含新 IT)。
- [ ] **文档**:新建 `docs/operations/manifest-driven-intake.md`:manifest JSON 格式、字段、`.manifest.json` 后缀、`batch.file.reception.group.*` 配置、组状态机、对账(MD5+条数)、EXPIRED 挂起需人工、灰度开关默认关。
- [ ] 提交 `test(intake): manifest 驱动 IT 三场景 + 文档`。

---

## Self-Review 结论

- **Spec 覆盖**:迁移+表+seed(T1)/实体仓库(T2)/manifest 解析(T3)/建组绑定(T4)/对账(T5)/到齐判定+触发+告警(T6)/job+钩子+配置(T7)/IT+文档(T8)。spec 各节有 Task。bundle 边界(只"等齐+对账+放行")保持,不实现 bundle 执行单元。
- **占位符**:T2/T4/T5/T6/T7 给出逻辑契约 + 关键代码 + "先 Read 现有文件按风格实现"(实体/IT 样板较多,要求对齐既有 FileReceptionQueue/ReconcileJobConfigIT 风格)。T1 的 alert CHECK 扩展要求先 Read V1_33。
- **类型一致**:`ParsedManifest(manifestId, sourceSystem, bizDate, files: List<ExpectedFile(fileName,expectedRecordCount,checksum,checksumAlgorithm,required)>)`、`ReceptionGroupStatus` 5 态、service 方法签名跨 Task 一致。
- **风险**:V1_38 避开审计 V1_36/37;manifest CHECK 扩展需对齐 V1_33 实际写法;FileReceptionService/OperationalTaskJobConfig 构造器变更要补既有测试 mock;对账复用接收 MD5 零重算。
