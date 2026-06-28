# 声明式映射接线(#2 Phase 1 wiring)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`).

**Goal:** 把已就绪的 MappingEngine/feed_definition/field_mapping/attributes 接进导入链路:当 job 参数带 `feedId` 时,按列名声明式映射原始列 → name/description/attributes + 配置化 business_key;**不带 feedId 时,默认导入路径字节级不变**。

**Architecture:** `FileRecord` 加可空 `rawValues`(原始列 map)+ `attributes`(映射溢出)。feed 模式:reader 捕获表头、产带 rawValues 的 FileRecord → `MappingImportProcessor` 应用 MappingEngine 填 name/description/attributes → writer 按 `business_key_fields` 拼键 + 写 attributes(JSONB)。默认模式:rawValues=null,processor/writer 走原逻辑。

**Tech Stack:** Java 21 / Spring Batch / JPA(JSONB)/ Testcontainers。

**设计依据:** `docs/superpowers/specs/2026-06-27-declarative-mapping-and-bundle-design.md`(Phase 1 接线部分)。地基(MappingEngine/TransformOp/FeedDefinition/FieldMapping/RowData/V1_39 attributes 列)已在 main。

**已核实(main):**
- `FileRecord`:id/name/description/lineNo(显式 getter/setter)。
- `CsvRecordLineParser.parse(line)`:按位置 fields[0]→id、[1]→name、[2]→description。
- `ImportContext.buildBusinessKey(name)` = `name:batchDate`,被 `BatchChunkImportStrategy` + `FileImportRecordWriter.dedup` 用。
- `ImportedRecordPartitioned`:business_key/name/description/batch_date/partition_key/...(**V1_39 已加 attributes JSONB 列,但实体无字段**)。
- chunk 全程 item 类型 = FileRecord。
- 地基:`mapping.MappingEngine.apply(List<MappingRule>, Map) → Map`、`MappingRule(sourceColumn,targetField,TransformOp,arg,required)`、`TransformOp{NONE,TRIM,UPPER,LOWER,DATE_FORMAT,DEFAULT}`、`FieldMappingRepository.findByFeedIdAndEnabledTrueOrderByOrderNoAsc`、`FeedDefinitionRepository.findByFeedIdAndEnabledTrue`。

**最高风险与铁律:**
- business_key/dedup/唯一索引口径改错 → 重复落库或误判丢数据。**铁律:无 feedId 时 business_key 与落库结果与改造前字节级一致**,由 Task 7 的对照回归测试守门。
- 所有热路径改动用"可空字段 + 默认分支不变"实现。

---

## File Structure

修改:
- `model/FileRecord.java`(+ rawValues、attributes 可空字段)
- `model/ImportedRecordPartitioned.java`(+ attributes 字段映射 JSONB)
- `batch/writer/strategy/ImportContext.java`(business_key 配置化,默认不变)
- `batch/reader/FileImportRecordReader.java`(feed 模式:捕获表头产 rawValues)
- `batch/processor/FileImportRecordProcessor.java` 或新增 `MappingImportProcessor.java`(feed 模式映射)
- `batch/writer/strategy/BatchChunkImportStrategy.java` + `PerRecordChunkImportStrategy.java`(写 attributes + 配置 business_key)
- `batch/config/FileImportJobConfig.java`(feedId 路由)
- `params/ImportJobParams.java`(+ feedId 参数)

新增:
- `db/migration/V1_40__feed_seed_default.sql`(seed 一个复刻现有行为的对照 feed)

测试:`unit/...`(各组件)+ IT `integration/DeclarativeMappingWiringIT`(字节级对照 + feed 映射端到端)。

---

## Task 1: FileRecord + ImportedRecordPartitioned 加可空字段(零影响)

**Files:** Modify `model/FileRecord.java`、`model/ImportedRecordPartitioned.java`;Test `unit/model/FileRecordAttributesTest.java`

- [ ] **Step 1: 测试**(默认 null + 可 set):
```java
package com.example.filebatchprocessor.unit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.filebatchprocessor.model.FileRecord;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileRecordAttributesTest {
    @Test
    void rawValuesAndAttributesDefaultNull() {
        FileRecord r = new FileRecord();
        assertNull(r.getRawValues());
        assertNull(r.getAttributes());
    }

    @Test
    void canSetRawValuesAndAttributes() {
        FileRecord r = new FileRecord();
        r.setRawValues(Map.of("c", "v"));
        r.setAttributes(Map.of("k", "x"));
        assertEquals("v", r.getRawValues().get("c"));
        assertEquals("x", r.getAttributes().get("k"));
    }
}
```

- [ ] **Step 2:** 红。

- [ ] **Step 3:** `FileRecord` 加 `private java.util.Map<String,Object> rawValues;` + `private java.util.Map<String,Object> attributes;` + 显式 getter/setter(照现有风格)。`ImportedRecordPartitioned` 加 `@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) @Column(name="attributes") private java.util.Map<String,Object> attributes;` + getter/setter(参考 business_job_instance 的 JSONB 映射写法;若项目用别的 JSON 映射方式,Read 一个现有 JSONB 实体照搬)。

- [ ] **Step 4:** 绿;`./mvnw -DskipTests test-compile` SUCCESS。
- [ ] **Step 5:** commit `feat(mapping): FileRecord rawValues/attributes + ImportedRecordPartitioned.attributes(可空,默认零影响)`。

---

## Task 2: ImportContext business_key 配置化(默认字节级不变)

**Files:** Modify `batch/writer/strategy/ImportContext.java`;Test `unit/batch/writer/ImportContextTest.java`

- [ ] **Step 1: 测试**(默认 = name:batchDate 不变;配置多字段 = 按字段拼):
```java
package com.example.filebatchprocessor.unit.batch.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.batch.writer.strategy.ImportContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportContextTest {
    @Test
    void defaultBusinessKeyUnchanged() {
        ImportContext ctx = new ImportContext("2026-06-28", 1L, "f.csv", null);
        assertEquals("alice:2026-06-28", ctx.buildBusinessKey("alice"));
    }

    @Test
    void configuredFieldsBusinessKey() {
        ImportContext ctx = new ImportContext("2026-06-28", 1L, "f.csv", List.of("name", "category"));
        // 多字段时用 mapped 行拼键
        assertEquals("alice|food:2026-06-28",
                ctx.buildBusinessKeyFromFields(Map.of("name", "alice", "category", "food")));
    }
}
```

- [ ] **Step 2:** 红。

- [ ] **Step 3:** `ImportContext` record 加第 4 个组件 `List<String> businessKeyFields`(可空);现有 3 参用法全改为 4 参传 null(或加一个兼容静态工厂/紧凑构造)。**`buildBusinessKey(String name)` 实现完全不变**(`name:batchDate`),保证默认路径字节级一致。新增 `buildBusinessKeyFromFields(Map<String,Object> mappedRow)`:若 businessKeyFields 为空 → 退回 `buildBusinessKey(String.valueOf(mappedRow.get("name")))`;否则按 businessKeyFields 顺序取值用 `|` 连接 + `:batchDate`。
  - **注意**:现有调用 `new ImportContext(batchDate, jobExecId, inputFileName)` 的所有地方(FileImportRecordWriter/BatchChunkImportStrategy/测试)都要补第 4 参 null —— grep `new ImportContext(` 全改,既有测试一并改。

- [ ] **Step 4:** 绿;全量 `./mvnw test -Dtest='ImportContext*,BatchChunkImportStrategy*,FileImportRecordWriter*' -q` 相关绿(确认默认 business_key 没变)。
- [ ] **Step 5:** commit `feat(mapping): ImportContext business_key 配置化(默认 name:batchDate 不变)`。

---

## Task 3: MappingImportProcessor(feed 模式映射,默认走原 processor)

**Files:** Create `batch/processor/MappingImportProcessor.java`;Test `unit/batch/processor/MappingImportProcessorTest.java`

> 设计:MappingImportProcessor 包装/替代默认 processor。构造时拿到 `List<MappingRule>`(来自 feed 的 field_mapping)。process(FileRecord):若 record.getRawValues()!=null(feed 模式)→ MappingEngine.apply(rules, rawValues) → 把 mapped 里 "name"/"description" set 到 record,其余 key set 到 record.attributes;若 rawValues==null(默认)→ 退回原 FileImportRecordProcessor 行为(name 转大写 + 校验)。

- [ ] **Step 1: 测试**:
  - feed 模式:rawValues={c_name:"alice",c_cat:"food"},rules=[UPPER c_name→name, NONE c_cat→category] → record.name="ALICE",record.attributes={category:"food"}。
  - 默认模式:rawValues=null,record.name="bob" → 退回原行为(name="BOB")。
  - (默认模式可注入一个真实/mock 的 FileImportRecordProcessor 作 delegate。)

- [ ] **Step 2-5:** 实现(@Component 或由 JobConfig new;持 delegate FileImportRecordProcessor + rules);TDD 绿;commit `feat(mapping): MappingImportProcessor(feed 模式映射,默认委托原 processor)`。

---

## Task 4: Reader feed 模式(捕获表头,产 rawValues)

**Files:** Modify `batch/reader/FileImportRecordReader.java`(+ 一个 feed 标志 + 表头列名);Test `unit/batch/reader/FileImportRecordReaderFeedModeTest.java`

> 设计:给 reader 加一个可选 `List<String> feedHeaderColumns`(非空=feed 模式)。**仅对行格式(CSV)feed 支持**(JSON/Excel feed 后置)。feed 模式:首行表头按分隔符拆成列名(或用注入的 feedHeaderColumns),后续每行拆成 `Map<列名,值>` set 到 FileRecord.rawValues(name/description 留空,由 processor 填)。默认模式(feedHeaderColumns=null)行为完全不变。lineNo/分片/checksum 逻辑复用现有。

- [ ] 实现要点:reader 构造器加可空 `boolean feedMode`(或 List<String> feedHeaderColumns);read() 在行模式分支内,若 feedMode:第一行作表头存列名,数据行 split 后 zip 成 rawValues map → FileRecord.setRawValues;非 feedMode 走现有 lineParser。测试:feed 模式产出带 rawValues 的记录、表头正确、分片仍按行;默认模式不受影响(跑现有 reader 测试绿)。
- [ ] TDD 绿 + 现有 reader 测试绿;commit `feat(mapping): FileImportRecordReader feed 模式(CSV 原始列)`。

---

## Task 5: Writer/strategy 写 attributes + 配置 business_key

**Files:** Modify `batch/writer/strategy/BatchChunkImportStrategy.java`、`PerRecordChunkImportStrategy.java`;复用现有测试 + 补。

> 设计:建实体时,`entity.setAttributes(item.getAttributes())`(null 则不设,默认零影响);business_key 用 `context.buildBusinessKeyFromFields(mappedRow)` —— 但 strategy 拿到的是 FileRecord;约定:feed 模式下 mappedRow = {name,description}+attributes 已在 record 上,business_key 从 record 的 name(+ 若 businessKeyFields 配置则从 record.attributes/字段)拼。**简化且安全**:Task 2 的 buildBusinessKeyFromFields 接受一个 Map;strategy 构造该 Map = {"name":record.name,"description":record.description} ∪ record.attributes,传入。businessKeyFields 为空时退回 name:batchDate(默认不变)。

- [ ] 实现:两个 strategy 建 entity 处加 setAttributes;business_key 改用 buildBusinessKeyFromFields(组装 record 的字段+attributes map)。**默认路径(attributes=null、businessKeyFields=null)结果必须与原来一致**——buildBusinessKeyFromFields 在 businessKeyFields 空时 == buildBusinessKey(name)。dedup(FileImportRecordWriter)同口径改。
- [ ] 跑现有 BatchChunkImportStrategy/Writer 测试 + Task 7 对照回归。commit `feat(mapping): writer 写 attributes + 配置化 business_key(默认不变)`。

---

## Task 6: ImportJobParams feedId + FileImportJobConfig 路由 + V1_40 seed

**Files:** Modify `params/ImportJobParams.java`、`batch/config/FileImportJobConfig.java`;Create `db/migration/V1_40__feed_seed_default.sql`

- [ ] ImportJobParams 加 `feedId`(String,可空)getter。
- [ ] FileImportJobConfig:importReader/importProcessor/importWriter @StepScope bean 里,若 params.getFeedId() 非空 → 加载 FeedDefinition + FieldMapping(注入 FeedDefinitionRepository/FieldMappingRepository),构造 feed 模式 reader(传表头/feedMode)、MappingImportProcessor(传 rules)、ImportContext(传 businessKeyFields);否则默认。**默认分支保持现状**。
- [ ] V1_40 seed 一个对照 feed `default-csv`:format=CSV,business_key_fields=null(=默认口径),field_mapping 复刻现有 positional(source_column 用列名约定 col0/col1/col2 或表头 id/name/description → target name/description,transform UPPER 复刻 processor 的 name 转大写)。用于 Task 7 对照。
- [ ] 编译 + 既有测试绿(补构造变更的 mock);commit `feat(mapping): feedId 路由 + V1_40 对照 seed feed`。

---

## Task 7: 字节级对照回归 IT + 文档(守门)

**Files:** Test `integration/DeclarativeMappingWiringIT.java`;更新 `docs/operations/declarative-mapping.md`

- [ ] **对照回归 IT(关键守门)**:同一份 CSV,分别走
  1. **默认路径**(无 feedId)导入 → 读 imported_records_partition 的 (business_key,name,description) 集合 A;
  2. **feed 路径**(feedId=对照 feed,映射复刻默认语义)导入到不同 batchDate → 集合 B;
  断言 A 与 B 在 (name,description, business_key 去掉 batchDate 部分) 上**逐行一致**(证明 feed 路径复刻默认语义、business_key 口径没漂)。
  再加一个 feed **真映射**用例:两列源 c1,c2 映射到 name + attributes,断言 attributes JSONB 落库正确。
- [ ] **全量回归**:`./mvnw test -Punit-test`(0 失败)+ `./mvnw test -Pintegration-test`(0 失败,含新 IT + 默认导入 IT 仍绿 = 默认路径没破)。
- [ ] **文档**:更新 `declarative-mapping.md`——把"尚未接入导入链路"改为"已接入(feedId 路由);不带 feedId 默认路径不变",补 feedId 用法 + business_key_fields/attributes 说明。
- [ ] commit `test(mapping): 接线字节级对照回归 IT + 文档`。

---

## Self-Review 结论

- **Spec 覆盖**:可空字段(T1)/ business_key 配置化(T2)/ 映射 processor(T3)/ feed reader(T4)/ writer attributes+键(T5)/ feedId 路由+seed(T6)/ 对照回归+文档(T7)。
- **铁律(默认路径不变)**:T2 buildBusinessKey 原样保留、T5 attributes=null 不设、T1 字段默认 null、T6 默认分支不变;T7 对照回归 IT + 既有导入 IT 双重守门。
- **类型一致**:`ImportContext(batchDate,jobExecId,inputFileName,businessKeyFields)`、`buildBusinessKeyFromFields(Map)`、`FileRecord.rawValues/attributes`、`MappingEngine.apply(rules,map)` 跨 Task 一致。
- **风险控制**:JSON/Excel feed、Phase 2 bundle 不在本计划;business_key 多字段化有对照回归守门;每个热路径改动都"可空+默认分支不变"。
