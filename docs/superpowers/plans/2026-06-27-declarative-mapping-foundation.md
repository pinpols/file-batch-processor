# 声明式映射 — 安全地基版(#2 Phase 1 foundation)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`).

**Goal:** 落地声明式映射的**低风险地基**:配置表(feed_definition/field_mapping)+ 纯函数 MappingEngine(6 算子)+ 核心表加 `attributes JSONB` 列。**不改 business_key/dedup/导入热路径的默认行为**,接线(config-driven processor/writer)留作下一步增量。

**Architecture:** 纯加法。MappingEngine 是无副作用纯函数:吃 `List<FieldMapping>` + 原始行 `Map<String,Object>` → 输出映射后 `Map<String,Object>`(应用 trim/大小写/日期/默认值 + 必填校验)。配置存 feed_definition/field_mapping 表。`imported_records_partition` 加可空 `attributes JSONB`(承接将来超出 name/description 的字段)。现有导入链路一行不改。

**Tech Stack:** Java 21 / JPA / PostgreSQL JSONB / Testcontainers(IT 验 ddl)。

**设计依据:** `docs/superpowers/specs/2026-06-27-declarative-mapping-and-bundle-design.md`(本计划只做其 Phase 1 的"地基"子集:配置 + 引擎 + 列,不含 business_key 多字段化与 processor/writer 接线)。

**已核实(main 分支):**
- 导入热路径:`ImportContext.buildBusinessKey(name)` = `name:batchDate`;`FileImportRecordProcessor`(name 转大写);`BatchChunkImportStrategy` 建 `ImportedRecordPartitioned`;`FileImportRecordWriter.dedup` 用 buildBusinessKey。**本计划全部不碰**。
- `ImportedRecordPartitioned`:business_key(200)/name(200)/description(500)/batch_date/partition_key/created_at/updated_at/checksum/source_file_name。**无 attributes 列**(本计划加)。
- 最新迁移 V1_38;本计划用 **V1_39**。
- JSONB 映射先例:`business_job_instance` 的 request_payload/result_summary 用 JSONB(可参考其 JPA @JdbcTypeCode 写法)。

**范围边界(YAGNI,明确不做):**
- 不改 FileRecord、不改 business_key 口径、不改 dedup/唯一索引、不动 processor/writer。
- 不做 config-driven 导入链路接线(下一步增量)。
- 算子只 6 个;不做正则/表达式/跨字段/查表。

---

## File Structure

新增:
- `db/migration/V1_39__feed_field_mapping_and_attributes.sql`
- `mapping/TransformOp.java`(enum)
- `mapping/MappingEngine.java`(@Component,纯函数)
- `model/FeedDefinition.java`、`FieldMapping.java`、`model/RowData.java`(值类型,前瞻)
- `repository/FeedDefinitionRepository.java`、`FieldMappingRepository.java`

测试:`unit/mapping/MappingEngineTest`、IT `integration/DeclarativeMappingSchemaIT`(ddl validate 新表/列)。

---

## Task 1: Flyway V1_39(配置表 + attributes 列)

**Files:** Create `src/main/resources/db/migration/V1_39__feed_field_mapping_and_attributes.sql`

- [ ] **Step 1: 写迁移**
```sql
-- 声明式映射地基:feed 定义 + 字段映射 + imported_records_partition 加 attributes(#2 Phase 1)

CREATE TABLE IF NOT EXISTS feed_definition (
    feed_id VARCHAR(100) PRIMARY KEY,
    feed_name VARCHAR(200),
    format VARCHAR(20) NOT NULL DEFAULT 'CSV',
    delimiter VARCHAR(8) DEFAULT ',',
    has_header BOOLEAN NOT NULL DEFAULT TRUE,
    target_table VARCHAR(100) NOT NULL DEFAULT 'imported_records_partition',
    business_key_fields VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS field_mapping (
    id BIGSERIAL PRIMARY KEY,
    feed_id VARCHAR(100) NOT NULL REFERENCES feed_definition(feed_id),
    source_column VARCHAR(200) NOT NULL,
    target_field VARCHAR(200) NOT NULL,
    transform_op VARCHAR(20) NOT NULL DEFAULT 'NONE',
    transform_arg VARCHAR(200),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    order_no INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_field_mapping_target UNIQUE (feed_id, target_field)
);
CREATE INDEX IF NOT EXISTS idx_field_mapping_feed ON field_mapping(feed_id);

-- 核心表加 attributes JSONB(纯加可空列,PG 不重写表;承接将来超出 name/description 的映射字段)
ALTER TABLE imported_records_partition ADD COLUMN IF NOT EXISTS attributes JSONB;
```

- [ ] **Step 2: 验证**(本机有 PG)由 Task 5 IT 跑 Flyway 验证;此处 `./mvnw -q -DskipTests test-compile` SUCCESS。

- [ ] **Step 3: 提交**
```bash
git add src/main/resources/db/migration/V1_39__feed_field_mapping_and_attributes.sql
git commit -m "feat(mapping): V1_39 feed_definition/field_mapping + imported_records_partition.attributes"
```

---

## Task 2: TransformOp + MappingEngine(纯函数)

**Files:**
- Create: `mapping/TransformOp.java`、`mapping/MappingEngine.java`
- Test: `unit/mapping/MappingEngineTest.java`

> MappingEngine 不依赖实体——它吃"映射规则列表"和"原始行 Map",输出"映射后 Map"。为解耦实体,定义一个轻量入参接口或直接用一个 record `MappingRule(sourceColumn, targetField, TransformOp op, String arg, boolean required)`。本任务用 record `MappingRule` 作引擎入参(实体→MappingRule 的转换在将来接线时做,本任务不碰实体)。

- [ ] **Step 1: 写失败测试**
```java
package com.example.filebatchprocessor.unit.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.filebatchprocessor.mapping.MappingEngine;
import com.example.filebatchprocessor.mapping.MappingEngine.MappingRule;
import com.example.filebatchprocessor.mapping.TransformOp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingEngineTest {

    private final MappingEngine engine = new MappingEngine();

    @Test
    void appliesTrimUpperLowerDefault() {
        var rules = List.of(
                new MappingRule("c_name", "name", TransformOp.UPPER, null, true),
                new MappingRule("c_desc", "description", TransformOp.TRIM, null, false),
                new MappingRule("c_cat", "category", TransformOp.DEFAULT, "N/A", false));
        Map<String, Object> src = Map.of("c_name", "alice", "c_desc", "  hi  ", "c_cat", "");
        Map<String, Object> out = engine.apply(rules, src);
        assertEquals("ALICE", out.get("name"));
        assertEquals("hi", out.get("description"));
        assertEquals("N/A", out.get("category"));
    }

    @Test
    void dateFormatToIso() {
        var rules = List.of(new MappingRule("d", "bizDate", TransformOp.DATE_FORMAT, "yyyy/MM/dd", false));
        Map<String, Object> out = engine.apply(rules, Map.of("d", "2026/06/27"));
        assertEquals("2026-06-27", out.get("bizDate"));
    }

    @Test
    void requiredMissingThrows() {
        var rules = List.of(new MappingRule("c", "name", TransformOp.NONE, null, true));
        assertThrows(IllegalArgumentException.class, () -> engine.apply(rules, Map.of("c", "")));
    }
}
```

- [ ] **Step 2:** `./mvnw test -Dtest=MappingEngineTest -q` 红。

- [ ] **Step 3: 实现**

`TransformOp.java`:
```java
package com.example.filebatchprocessor.mapping;

public enum TransformOp {
    NONE,
    TRIM,
    UPPER,
    LOWER,
    DATE_FORMAT,
    DEFAULT
}
```

`MappingEngine.java`:
```java
package com.example.filebatchprocessor.mapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 声明式字段映射纯函数:应用算子 + 必填校验。无副作用,可独立测试。 */
@Component
public class MappingEngine {

    /** 映射规则(实体 FieldMapping → 本 record 的转换在接线时做,引擎本身不依赖实体)。 */
    public record MappingRule(
            String sourceColumn, String targetField, TransformOp op, String arg, boolean required) {}

    public Map<String, Object> apply(List<MappingRule> rules, Map<String, Object> sourceRow) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (MappingRule rule : rules) {
            Object raw = sourceRow.get(rule.sourceColumn());
            Object value = applyOp(rule.op(), rule.arg(), raw);
            if (rule.required() && (value == null || String.valueOf(value).isBlank())) {
                throw new IllegalArgumentException("required target field missing: " + rule.targetField());
            }
            out.put(rule.targetField(), value);
        }
        return out;
    }

    private Object applyOp(TransformOp op, String arg, Object raw) {
        String s = raw == null ? null : String.valueOf(raw);
        return switch (op) {
            case NONE -> s;
            case TRIM -> s == null ? null : s.trim();
            case UPPER -> s == null ? null : s.toUpperCase(Locale.ROOT);
            case LOWER -> s == null ? null : s.toLowerCase(Locale.ROOT);
            case DEFAULT -> (s == null || s.isBlank()) ? arg : s;
            case DATE_FORMAT -> formatDate(s, arg);
        };
    }

    private String formatDate(String s, String sourcePattern) {
        if (s == null || s.isBlank()) {
            return s;
        }
        DateTimeFormatter src = DateTimeFormatter.ofPattern(
                (sourcePattern == null || sourcePattern.isBlank()) ? "yyyy-MM-dd" : sourcePattern);
        return LocalDate.parse(s.trim(), src).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
```

- [ ] **Step 4:** `./mvnw test -Dtest=MappingEngineTest -q` 绿(3 PASS)。**Step 5:** 提交 `feat(mapping): MappingEngine(6 算子纯函数)+ TransformOp`。

---

## Task 3: FeedDefinition / FieldMapping 实体 + 仓库 + RowData

**Files:**
- Create: `model/FeedDefinition.java`、`FieldMapping.java`、`RowData.java`、`repository/FeedDefinitionRepository.java`、`FieldMappingRepository.java`
- Test: `unit/model/RowDataTest.java`(简单值类型测试)

> 实体注解风格对齐现有 `model/`(Lombok @Getter/@Setter,见 FileReceptionQueue/ReceptionGroup),字段/列名对齐 V1_39。

- [ ] **Step 1: 写 RowData 测试**
```java
package com.example.filebatchprocessor.unit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.model.RowData;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RowDataTest {
    @Test
    void holdsValuesAndLineNo() {
        RowData r = new RowData(Map.of("a", "1"), 5L);
        assertEquals("1", r.values().get("a"));
        assertEquals(5L, r.lineNo());
    }
}
```

- [ ] **Step 2:** 红。

- [ ] **Step 3: 实现**

`RowData.java`:
```java
package com.example.filebatchprocessor.model;

import java.util.Map;

/** 通用行:列名→值 + 行号。供将来 config-driven 导入链路使用。 */
public record RowData(Map<String, Object> values, long lineNo) {}
```

`FeedDefinition.java` — @Entity @Table(name="feed_definition"),@Id String feedId(name="feed_id"),feedName、format(默认 "CSV")、delimiter(默认 ",")、Boolean hasHeader(name="has_header")、targetTable(name="target_table")、businessKeyFields(name="business_key_fields")、Boolean enabled、createdAt、updatedAt。Lombok @Getter/@Setter。

`FieldMapping.java` — @Entity @Table(name="field_mapping"),@Id @GeneratedValue Long id,feedId(name="feed_id")、sourceColumn(name="source_column")、targetField(name="target_field")、@Enumerated(EnumType.STRING) TransformOp transformOp(name="transform_op",默认 NONE)、transformArg(name="transform_arg")、boolean required、Integer orderNo(name="order_no")、Boolean enabled。Lombok。

`FeedDefinitionRepository extends JpaRepository<FeedDefinition, String>`:`Optional<FeedDefinition> findByFeedIdAndEnabledTrue(String feedId)`。
`FieldMappingRepository extends JpaRepository<FieldMapping, Long>`:`List<FieldMapping> findByFeedIdAndEnabledTrueOrderByOrderNoAsc(String feedId)`。

- [ ] **Step 4:** RowDataTest 绿;`./mvnw -DskipTests test-compile` SUCCESS。**Step 5:** 提交 `feat(mapping): FeedDefinition/FieldMapping 实体+仓库 + RowData`。

---

## Task 4: IT(ddl validate 新表/列)+ 文档

**Files:** Test `integration/DeclarativeMappingSchemaIT.java`;文档。

- [ ] **Step 1: 写 IT**(@SpringBootTest @ActiveProfiles("test") 继承 PostgresContainerSupport,参考现有 repository IT):
  - 注入 FeedDefinitionRepository、FieldMappingRepository、ImportedRecordPartitionedRepository。
  - save 一个 FeedDefinition + 两个 FieldMapping(含 transform_op),read 回断言字段对得上(证明实体↔V1_39 列对齐,ddl validate/update 通过 = 迁移与实体一致)。
  - save 一个 ImportedRecordPartitioned 并 set 一个 attributes(JSONB)——若实体加了 attributes 字段则验;**注意:本地基版未给 ImportedRecordPartitioned 实体加 attributes 字段**(列加了但实体没映射,ddl validate 对"表有列实体没字段"是允许的,不报错)。所以本 IT 只验 feed/field_mapping 两表 + 一条原 ImportedRecordPartitioned 正常存读(证明加列没破坏既有实体)。
- [ ] **Step 2: 全量回归** `./mvnw test -Punit-test`(0 失败)+ 本机有 PG 时 `./mvnw test -Pintegration-test`(含新 IT,Flyway 跑 V1_39)。
- [ ] **Step 3: 文档** 新建 `docs/operations/declarative-mapping.md`:说明这是地基版——feed_definition/field_mapping 表结构、MappingEngine 的 6 算子语义(NONE/TRIM/UPPER/LOWER/DATE_FORMAT/DEFAULT + required)、attributes JSONB 列用途;**明确标注:本版仅提供配置+引擎+列,尚未接入导入链路(business_key 多字段化与 config-driven processor/writer 为后续增量),现有 importJob 行为零变化**。
- [ ] **Step 4: 提交** `test(mapping): 地基 schema IT + 文档(标注未接线)`。

---

## Self-Review 结论

- **Spec 覆盖**:配置表+attributes(T1)/ MappingEngine 6 算子(T2)/ 实体仓库+RowData(T3)/ IT+文档(T4)。覆盖 design Phase 1 的"地基"子集;business_key 多字段化与 processor/writer 接线**明确不在本计划**(文档标注)。
- **零热路径风险**:不改 FileRecord/ImportContext/processor/writer/dedup/唯一索引;attributes 纯加可空列(PG 不重写);MappingEngine 是独立纯函数未被链路调用。现有 importJob 字节级零变化。
- **类型一致**:`MappingEngine.MappingRule(sourceColumn,targetField,TransformOp,arg,required)`、`TransformOp` 6 值、`RowData(values, lineNo)`、实体列名对齐 V1_39。
- **下一步(不在本计划)**:接线 = 把 FieldMapping 实体→MappingRule、reader 产 RowData、config-driven processor 调 MappingEngine、writer 按 business_key_fields 拼键 + 写 attributes;需 seed feed 字节级对照回归守幂等。
