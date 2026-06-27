# 导入多格式(#4)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让导入支持 JSON(对象数组)与 Excel(.xlsx),与现有 CSV/定长行解析并存,加格式不改核心写入链路。

**Architecture:** 新增文档级 `DocumentRecordReader` SPI(把整个 Resource 解析成 `Iterator<FileRecord>`),与现有行级 `RecordLineParser` 并存;`FileImportRecordReader` 按 fileFormat 二选一路由(行模式不变;文档模式从迭代器逐条产出 + 按 record 序号分片/重启)。JSON 走 Jackson streaming,Excel 复用 Hutool ExcelReader(需补 poi-ooxml)。

**Tech Stack:** Spring Boot 4 / Java 21 / Spring Batch ItemStreamReader / Jackson(已有)/ Hutool ExcelUtil + poi-ooxml(本计划新增)。

**设计依据:** `docs/superpowers/specs/2026-06-27-import-multi-format-design.md`

**已核实的现状:**
- `FileRecord`(model):字段 id(Long)、name(String)、description(String)、lineNo(Long),有显式 getter/setter。
- 行 SPI:`batch/reader/spi/RecordLineParser.parse(String)`、`RecordLineParserProvider.{supports,create}`、`RecordLineParserFactory.create(format,delimiter)`(按 supports 选 provider,默认 CSV)。
- `FileImportRecordReader`(`batch/reader/`)implements `ItemStreamReader<FileRecord>, StepExecutionListener`:构造器选 lineParser;`read()` 逐行 readLine + 跳首行表头 + 分片 `(lineCount-1)%shardTotal==shardIndex` + 每行 Adler32 checksum;`open()` 重启时 readLine 跳 `line.count` 行;`update()` 存 `line.count`/`checksum`。装配在 `FileImportJobConfig.importReader`(@StepScope)。
- `ImportJobParams`(`params/`):有 fileFormat/fileDelimiter/shardIndex/shardTotal。
- **依赖:Jackson `ObjectMapper` 已在 classpath;POI 不在依赖树(hutool-all 不传递 POI)→ 本计划 Task 1 必须显式加 `poi-ooxml`。**

**关键设计决策:**
- 文档模式下"分片"语义 = 按 **record 序号**取模(不是文件字节);"checksum" = 对 record 规范化字节(`id|name|description` UTF-8)累加(语义从"文件字节"变"记录内容",文档化);"重启恢复" = 跳过前 N 条 record(N=`record.count`)。
- 行模式(CSV/FIXED)代码**一行不改**,只在 reader 里加"文档模式"分支。
- XML 暂不做(SPI 预留扩展位)。

---

## File Structure

新增(`src/main/java/com/example/filebatchprocessor/batch/reader/spi/`):
- `DocumentReadOptions.java` — record(sheetIndex, sheetName)
- `DocumentRecordReader.java` — 接口:`Iterator<FileRecord> open(Resource)`,extends Closeable
- `DocumentRecordReaderProvider.java` — 接口:supports(format) + create(DocumentReadOptions)
- `DocumentRecordReaderFactory.java` — @Component,注入 List<provider>,create + supportsDocument
- `JsonDocumentRecordReader.java` + `JsonDocumentRecordReaderProvider.java`
- `ExcelDocumentRecordReader.java` + `ExcelDocumentRecordReaderProvider.java`

修改:
- `pom.xml` — 加 poi-ooxml
- `batch/reader/FileImportRecordReader.java` — 文档模式分支
- `params/ImportJobParams.java` — excel.sheet.index/name
- `batch/config/FileImportJobConfig.java` — 注入 DocumentRecordReaderFactory 透传

测试(`src/test/java/com/example/filebatchprocessor/unit/batch/reader/`):
- `JsonDocumentRecordReaderTest`、`ExcelDocumentRecordReaderTest`、`FileImportRecordReaderDocumentModeTest`

---

## Task 1: 加 poi-ooxml 依赖 + 验证 Excel 可用

**Files:** Modify `pom.xml`

- [ ] **Step 1: 确认 POI 当前不在依赖树**

Run: `./mvnw dependency:tree 2>/dev/null | grep -i poi || echo "NO POI"`
Expected: `NO POI`。

- [ ] **Step 2: 加依赖**

在 `pom.xml` `<dependencies>` 区(hutool 声明附近,line 122 之后)新增:
```xml
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.3.0</version>
        </dependency>
```
注:5.3.0 若拉不到改最近 5.3.x;Spring Boot parent 若已管理 poi 版本可去掉 `<version>`(先试带版本,冲突再调)。

- [ ] **Step 3: 验证可解析 + Hutool Excel 类可用**

Run: `./mvnw dependency:tree 2>/dev/null | grep -i 'poi-ooxml'`
Expected: 出现 `org.apache.poi:poi-ooxml`。
Run: `./mvnw -q -DskipTests test-compile`
Expected: BUILD SUCCESS(现有 FileExportService 的 ExcelUtil 用法此时有真实 POI 支撑)。

- [ ] **Step 4: 提交**

```bash
git add pom.xml
git commit -m "build: 加 poi-ooxml(Excel 导入/导出运行期支撑)"
```

---

## Task 2: DocumentRecordReader SPI + Factory

**Files:**
- Create: `.../spi/DocumentReadOptions.java`、`DocumentRecordReader.java`、`DocumentRecordReaderProvider.java`、`DocumentRecordReaderFactory.java`
- Test: `.../unit/batch/reader/DocumentRecordReaderFactoryTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.example.filebatchprocessor.unit.batch.reader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReader;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderProvider;
import com.example.filebatchprocessor.model.FileRecord;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

class DocumentRecordReaderFactoryTest {

    private static DocumentRecordReaderProvider provider(String fmt) {
        return new DocumentRecordReaderProvider() {
            public boolean supports(String format) {
                return fmt.equals(format);
            }

            public DocumentRecordReader create(DocumentReadOptions options) {
                return new DocumentRecordReader() {
                    public Iterator<FileRecord> open(Resource resource) {
                        return List.<FileRecord>of().iterator();
                    }

                    public void close() {}
                };
            }
        };
    }

    @Test
    void supportsDocumentTrueForRegisteredFormat() {
        DocumentRecordReaderFactory f = new DocumentRecordReaderFactory(List.of(provider("JSON")));
        assertTrue(f.supportsDocument("JSON"));
        assertFalse(f.supportsDocument("CSV"));
    }

    @Test
    void createReturnsMatchingReader() {
        DocumentRecordReaderFactory f = new DocumentRecordReaderFactory(List.of(provider("JSON")));
        DocumentRecordReader r = f.create("JSON", new DocumentReadOptions(null, null));
        org.junit.jupiter.api.Assertions.assertNotNull(r);
    }
}
```

- [ ] **Step 2: 跑 `./mvnw test -Dtest=DocumentRecordReaderFactoryTest -q` 确认编译失败**

- [ ] **Step 3: 实现四个类**

`DocumentReadOptions.java`:
```java
package com.example.filebatchprocessor.batch.reader.spi;

/** 文档解析选项(Excel sheet 选择等)。单租户内部够用。 */
public record DocumentReadOptions(Integer sheetIndex, String sheetName) {}
```

`DocumentRecordReader.java`:
```java
package com.example.filebatchprocessor.batch.reader.spi;

import com.example.filebatchprocessor.model.FileRecord;
import java.io.Closeable;
import java.util.Iterator;
import org.springframework.core.io.Resource;

/** 文档级记录读取:把整个资源解析成可迭代的 FileRecord 流(JSON 数组/Excel 工作簿)。 */
public interface DocumentRecordReader extends Closeable {

    /** 打开资源并返回惰性迭代器;实现内部持有底层流/解析器,close() 释放。 */
    Iterator<FileRecord> open(Resource resource) throws Exception;
}
```

`DocumentRecordReaderProvider.java`:
```java
package com.example.filebatchprocessor.batch.reader.spi;

/** 文档级 reader 的 provider SPI。新增格式 = 新增一个 @Component provider。 */
public interface DocumentRecordReaderProvider {

    boolean supports(String format);

    DocumentRecordReader create(DocumentReadOptions options);
}
```

`DocumentRecordReaderFactory.java`:
```java
package com.example.filebatchprocessor.batch.reader.spi;

import java.util.List;
import org.springframework.stereotype.Component;

/** 文档级 reader 工厂(对标 RecordLineParserFactory)。 */
@Component
public class DocumentRecordReaderFactory {

    private final List<DocumentRecordReaderProvider> providers;

    public DocumentRecordReaderFactory(List<DocumentRecordReaderProvider> providers) {
        this.providers = providers;
    }

    public boolean supportsDocument(String format) {
        if (format == null || format.isBlank()) {
            return false;
        }
        String f = format.trim().toUpperCase();
        return providers.stream().anyMatch(p -> p.supports(f));
    }

    public DocumentRecordReader create(String format, DocumentReadOptions options) {
        String f = format.trim().toUpperCase();
        return providers.stream()
                .filter(p -> p.supports(f))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported document format: " + f))
                .create(options);
    }
}
```

- [ ] **Step 4: 跑测试确认 2 PASS**

Run: `./mvnw test -Dtest=DocumentRecordReaderFactoryTest -q`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/filebatchprocessor/batch/reader/spi/Document*.java src/test/java/com/example/filebatchprocessor/unit/batch/reader/DocumentRecordReaderFactoryTest.java
git commit -m "feat(import): DocumentRecordReader SPI + 工厂(文档级解析骨架)"
```

---

## Task 3: JsonDocumentRecordReader(Jackson streaming)

**Files:**
- Create: `.../spi/JsonDocumentRecordReader.java`、`JsonDocumentRecordReaderProvider.java`
- Test: `.../unit/batch/reader/JsonDocumentRecordReaderTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.example.filebatchprocessor.unit.batch.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.batch.reader.spi.JsonDocumentRecordReader;
import com.example.filebatchprocessor.model.FileRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class JsonDocumentRecordReaderTest {

    @Test
    void parsesJsonArrayToFileRecords() throws Exception {
        String json = "[{\"id\":1,\"name\":\"a\",\"description\":\"x\"},{\"name\":\"b\"}]";
        JsonDocumentRecordReader reader = new JsonDocumentRecordReader(new ObjectMapper());
        Iterator<FileRecord> it = reader.open(new ByteArrayResource(json.getBytes()));
        List<FileRecord> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        reader.close();

        assertEquals(2, out.size());
        assertEquals(1L, out.get(0).getId());
        assertEquals("a", out.get(0).getName());
        assertEquals("x", out.get(0).getDescription());
        assertEquals("b", out.get(1).getName());
    }
}
```

- [ ] **Step 2: 跑 `./mvnw test -Dtest=JsonDocumentRecordReaderTest -q` 确认编译失败**

- [ ] **Step 3: 实现**

`JsonDocumentRecordReader.java`:
```java
package com.example.filebatchprocessor.batch.reader.spi;

import com.example.filebatchprocessor.model.FileRecord;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.springframework.core.io.Resource;

/** JSON 顶层对象数组的流式读取(Jackson streaming,不全量物化)。 */
public class JsonDocumentRecordReader implements DocumentRecordReader {

    private final ObjectMapper objectMapper;
    private InputStream in;
    private JsonParser parser;

    public JsonDocumentRecordReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Iterator<FileRecord> open(Resource resource) throws Exception {
        this.in = resource.getInputStream();
        this.parser = objectMapper.getFactory().createParser(in);
        JsonToken first = parser.nextToken();
        if (first != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException("JSON import expects a top-level array");
        }
        return new Iterator<>() {
            private Boolean hasNextCached;

            @Override
            public boolean hasNext() {
                if (hasNextCached != null) {
                    return hasNextCached;
                }
                try {
                    hasNextCached = parser.nextToken() == JsonToken.START_OBJECT;
                    return hasNextCached;
                } catch (Exception e) {
                    throw new RuntimeException("JSON parse error", e);
                }
            }

            @Override
            public FileRecord next() {
                if (hasNextCached == null) {
                    hasNext();
                }
                if (!Boolean.TRUE.equals(hasNextCached)) {
                    throw new NoSuchElementException();
                }
                hasNextCached = null;
                try {
                    JsonNode node = objectMapper.readTree(parser);
                    FileRecord r = new FileRecord();
                    if (node.hasNonNull("id")) {
                        r.setId(node.get("id").asLong());
                    }
                    if (node.hasNonNull("name")) {
                        r.setName(node.get("name").asText());
                    }
                    if (node.hasNonNull("description")) {
                        r.setDescription(node.get("description").asText());
                    }
                    return r;
                } catch (Exception e) {
                    throw new RuntimeException("JSON record parse error", e);
                }
            }
        };
    }

    @Override
    public void close() throws java.io.IOException {
        if (parser != null) {
            parser.close();
        }
        if (in != null) {
            in.close();
        }
    }
}
```

`JsonDocumentRecordReaderProvider.java`:
```java
package com.example.filebatchprocessor.batch.reader.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonDocumentRecordReaderProvider implements DocumentRecordReaderProvider {

    private final ObjectMapper objectMapper;

    public JsonDocumentRecordReaderProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String format) {
        return "JSON".equals(format);
    }

    @Override
    public DocumentRecordReader create(DocumentReadOptions options) {
        return new JsonDocumentRecordReader(objectMapper);
    }
}
```

- [ ] **Step 4: 跑测试确认 PASS**

- [ ] **Step 5: 提交**

```bash
git add src/main/java/.../spi/JsonDocument*.java src/test/java/.../unit/batch/reader/JsonDocumentRecordReaderTest.java
git commit -m "feat(import): JsonDocumentRecordReader(Jackson streaming)"
```

---

## Task 4: ExcelDocumentRecordReader(Hutool/POI)

**Files:**
- Create: `.../spi/ExcelDocumentRecordReader.java`、`ExcelDocumentRecordReaderProvider.java`
- Test: `.../unit/batch/reader/ExcelDocumentRecordReaderTest.java`

- [ ] **Step 1: 写失败测试(测试内用 Hutool ExcelWriter 现写 .xlsx 到临时文件再读回)**

```java
package com.example.filebatchprocessor.unit.batch.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions;
import com.example.filebatchprocessor.batch.reader.spi.ExcelDocumentRecordReader;
import com.example.filebatchprocessor.model.FileRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

class ExcelDocumentRecordReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesXlsxHeaderMappedRows(@TempDir Path dir) throws Exception {
        Path xlsx = dir.resolve("in.xlsx");
        try (ExcelWriter writer = ExcelUtil.getWriter(xlsx.toFile())) {
            writer.writeHeadRow(List.of("id", "name", "description"));
            writer.writeRow(List.of(1, "alice", "first"), false);
            writer.writeRow(List.of(2, "bob", "second"), false);
            writer.flush();
        }

        ExcelDocumentRecordReader reader = new ExcelDocumentRecordReader(new DocumentReadOptions(0, null));
        Iterator<FileRecord> it = reader.open(new FileSystemResource(xlsx.toFile()));
        List<FileRecord> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        reader.close();

        assertEquals(2, out.size());
        assertEquals(1L, out.get(0).getId());
        assertEquals("alice", out.get(0).getName());
        assertEquals("first", out.get(0).getDescription());
        assertEquals("bob", out.get(1).getName());
    }
}
```

- [ ] **Step 2: 跑 `./mvnw test -Dtest=ExcelDocumentRecordReaderTest -q` 确认编译失败**

- [ ] **Step 3: 实现**

`ExcelDocumentRecordReader.java`(Hutool readAll 返回 List<Map>,首行表头作 key;数值单元格 id 取整):
```java
package com.example.filebatchprocessor.batch.reader.spi;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.example.filebatchprocessor.model.FileRecord;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;

/** Excel(.xlsx)读取:首行表头按列名(id/name/description,大小写不敏感)映射。Hutool 全量读(单租户够用)。 */
public class ExcelDocumentRecordReader implements DocumentRecordReader {

    private final DocumentReadOptions options;
    private InputStream in;
    private ExcelReader excelReader;

    public ExcelDocumentRecordReader(DocumentReadOptions options) {
        this.options = options;
    }

    @Override
    public Iterator<FileRecord> open(Resource resource) throws Exception {
        this.in = resource.getInputStream();
        int sheetIndex = options != null && options.sheetIndex() != null ? options.sheetIndex() : 0;
        this.excelReader = (options != null && options.sheetName() != null && !options.sheetName().isBlank())
                ? ExcelUtil.getReader(in, options.sheetName())
                : ExcelUtil.getReader(in, sheetIndex);
        // 首行表头 → 后续行作 Map<列名,值>
        List<Map<String, Object>> rows = excelReader.readAll();
        List<FileRecord> records = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            records.add(toRecord(row));
        }
        return records.iterator();
    }

    private FileRecord toRecord(Map<String, Object> row) {
        FileRecord r = new FileRecord();
        Object id = get(row, "id");
        Object name = get(row, "name");
        Object desc = get(row, "description");
        if (id != null) {
            r.setId(toLong(id));
        }
        if (name != null) {
            r.setName(String.valueOf(name));
        }
        if (desc != null) {
            r.setDescription(String.valueOf(desc));
        }
        return r;
    }

    private static Object get(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        // POI 数值常读成 "1.0"
        return (long) Double.parseDouble(s);
    }

    @Override
    public void close() throws java.io.IOException {
        if (excelReader != null) {
            excelReader.close();
        }
        if (in != null) {
            in.close();
        }
    }
}
```

`ExcelDocumentRecordReaderProvider.java`:
```java
package com.example.filebatchprocessor.batch.reader.spi;

import org.springframework.stereotype.Component;

@Component
public class ExcelDocumentRecordReaderProvider implements DocumentRecordReaderProvider {

    @Override
    public boolean supports(String format) {
        return "EXCEL".equals(format) || "XLSX".equals(format);
    }

    @Override
    public DocumentRecordReader create(DocumentReadOptions options) {
        return new ExcelDocumentRecordReader(options);
    }
}
```

- [ ] **Step 4: 跑测试确认 PASS**

- [ ] **Step 5: 提交**

```bash
git add src/main/java/.../spi/ExcelDocument*.java src/test/java/.../unit/batch/reader/ExcelDocumentRecordReaderTest.java
git commit -m "feat(import): ExcelDocumentRecordReader(Hutool/POI, 表头列名映射)"
```

---

## Task 5: FileImportRecordReader 文档模式路由(核心集成)

**Files:**
- Modify: `batch/reader/FileImportRecordReader.java`
- Modify: `batch/config/FileImportJobConfig.java`(importReader 注入并透传 DocumentRecordReaderFactory + DocumentReadOptions)
- Test: `.../unit/batch/reader/FileImportRecordReaderDocumentModeTest.java`

> 实施者注意:先 Read `FileImportRecordReader.java` 全文与 `FileImportJobConfig.importReader` 全文,理解现有构造器/read/open/update/StepExecutionListener 后再改。下面给出改造要点与新增分支代码,**不要破坏行模式现有逻辑**。

- [ ] **Step 1: 写失败测试(文档模式:迭代到 null + 分片 + 重启跳过)**

```java
package com.example.filebatchprocessor.unit.batch.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.filebatchprocessor.batch.reader.FileImportRecordReader;
import com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory;
import com.example.filebatchprocessor.batch.reader.spi.JsonDocumentRecordReaderProvider;
import com.example.filebatchprocessor.model.FileRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.io.ByteArrayResource;

class FileImportRecordReaderDocumentModeTest {

    private DocumentRecordReaderFactory jsonFactory() {
        return new DocumentRecordReaderFactory(List.of(new JsonDocumentRecordReaderProvider(new ObjectMapper())));
    }

    private FileImportRecordReader reader(String json, Integer shardIndex, Integer shardTotal) {
        return new FileImportRecordReader(
                new ByteArrayResource(json.getBytes()),
                shardIndex,
                shardTotal,
                "JSON",
                null,
                null,
                jsonFactory(),
                new DocumentReadOptions(null, null));
    }

    @Test
    void readsAllJsonRecordsThenNull() throws Exception {
        FileImportRecordReader r =
                reader("[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]", 0, 1);
        r.open(new ExecutionContext());
        assertEquals("a", r.read().getName());
        assertEquals("b", r.read().getName());
        assertEquals("c", r.read().getName());
        assertNull(r.read());
    }

    @Test
    void shardingByRecordSeq() throws Exception {
        // shardTotal=2, shardIndex=0 → 取 record 序号 1,3(1-based,跳过 2)
        FileImportRecordReader r =
                reader("[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"},{\"name\":\"d\"}]", 0, 2);
        r.open(new ExecutionContext());
        FileRecord first = r.read();
        FileRecord second = r.read();
        assertEquals("a", first.getName());
        assertEquals("c", second.getName());
        assertNull(r.read());
    }
}
```

> 注:分片取模口径以实现为准,本测试假定与行模式一致(record 序号 1-based,`(seq-1)%shardTotal==shardIndex`,shardIndex=0 取 seq 1,3)。实现后若口径不同,据实修正断言但保持"分片不重叠、并集完整"。

- [ ] **Step 2: 跑 `./mvnw test -Dtest=FileImportRecordReaderDocumentModeTest -q` 确认编译失败(无该构造器)**

- [ ] **Step 3: 改造 FileImportRecordReader**

新增字段:
```java
    // 文档模式(JSON/Excel):非空表示走文档模式,行模式字段(reader/lineCount)不用
    private final com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReader documentReader;
    private java.util.Iterator<FileRecord> documentIterator;
    private long recordSeq = 0;
```

新增构造器(在现有 3 参/5 参/6 参构造器之外,新增 8 参;现有构造器把 documentReader 传 null):
```java
    public FileImportRecordReader(
            Resource resource,
            Integer shardIndex,
            Integer shardTotal,
            String format,
            String delimiter,
            RecordLineParserFactory parserFactory,
            com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory documentReaderFactory,
            com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions documentReadOptions) {
        this.resource = resource;
        this.shardIndex = shardIndex == null ? 0 : shardIndex;
        int total = shardTotal == null ? 1 : shardTotal;
        this.shardTotal = total <= 0 ? 1 : total;
        this.shardingEnabled = this.shardTotal > 1;
        if (documentReaderFactory != null && documentReaderFactory.supportsDocument(format)) {
            this.documentReader = documentReaderFactory.create(format, documentReadOptions);
            this.lineParser = null;
        } else {
            this.documentReader = null;
            this.lineParser = parserFactory != null
                    ? parserFactory.create(format, delimiter)
                    : new CsvRecordLineParser(delimiter); // 与现有 fallback 保持一致
        }
    }
```
(把现有 6 参构造器改为委托新 8 参构造器并传 documentReaderFactory=null、documentReadOptions=null;注意 `lineParser` 字段当前是 `final` —— 文档模式赋 null 即可,保持 final。若现有构造器对 lineParser 有 FIXED/CSV 特例分支,迁进 else 分支保持等价。)

`open()` 加文档模式分支(在现有行模式打开逻辑之前):
```java
    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (documentReader != null) {
            try {
                this.documentIterator = documentReader.open(resource);
                long skip = executionContext.containsKey("record.count")
                        ? executionContext.getLong("record.count")
                        : 0;
                for (long i = 0; i < skip && documentIterator.hasNext(); i++) {
                    documentIterator.next();
                    recordSeq++;
                }
            } catch (Exception e) {
                throw new ItemStreamException("Failed to open document reader", e);
            }
            return;
        }
        // ... 现有行模式 open 逻辑原样保留 ...
    }
```

`read()` 加文档模式分支(方法开头):
```java
    @Override
    public FileRecord read() throws Exception {
        if (documentReader != null) {
            while (documentIterator.hasNext()) {
                FileRecord record = documentIterator.next();
                recordSeq++;
                if (shardingEnabled && ((recordSeq - 1) % shardTotal) != shardIndex) {
                    continue;
                }
                record.setLineNo(recordSeq);
                checksum.update(documentChecksumBytes(record));
                return record;
            }
            return null;
        }
        // ... 现有行模式 read 逻辑原样保留 ...
    }

    private byte[] documentChecksumBytes(FileRecord r) {
        String s = (r.getId() == null ? "" : r.getId()) + "|"
                + (r.getName() == null ? "" : r.getName()) + "|"
                + (r.getDescription() == null ? "" : r.getDescription());
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
```

`update()` 文档模式存 record.count(在现有 update 里加分支):
```java
    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        if (documentReader != null) {
            executionContext.putLong("record.count", recordSeq);
            executionContext.putLong("checksum", checksum.getValue());
            return;
        }
        // ... 现有行模式 update 原样保留 ...
    }
```

`close()`(若有)文档模式关闭 documentReader;若 reader 没显式 close,加 ItemStream close 分支释放 documentReader。

- [ ] **Step 4: 改 FileImportJobConfig.importReader**

注入 `DocumentRecordReaderFactory documentReaderFactory`,从 ImportJobParams 取 sheet 选项构造 `DocumentReadOptions`,改用新 8 参构造器:
```java
    public FileImportRecordReader importReader(
            @Value("#{jobParameters}") Map<String, Object> jobParameters,
            RecordLineParserFactory recordLineParserFactory,
            com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory documentReaderFactory) {
        ImportJobParams params = ImportJobParams.from(jobParameters);
        params.validateForReader();
        Resource resource = /* 现有 PathSafety.confine + FileSystemResource/ClassPathResource 逻辑不变 */;
        var docOptions = new com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions(
                params.getExcelSheetIndex(), params.getExcelSheetName());
        return new FileImportRecordReader(
                resource,
                params.getShardIndex(),
                params.getShardTotal(),
                params.getFileFormat(),
                params.getFileDelimiter(),
                recordLineParserFactory,
                documentReaderFactory,
                docOptions);
    }
```
(getExcelSheetIndex/getExcelSheetName 由 Task 6 加。)

- [ ] **Step 5: 跑测试**

Run: `./mvnw test -Dtest=FileImportRecordReaderDocumentModeTest -q`
Expected: 2 PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/.../batch/reader/FileImportRecordReader.java src/main/java/.../batch/config/FileImportJobConfig.java src/test/java/.../unit/batch/reader/FileImportRecordReaderDocumentModeTest.java
git commit -m "feat(import): FileImportRecordReader 文档模式路由(JSON/Excel)"
```

---

## Task 6: ImportJobParams 加 excel.sheet 参数

**Files:**
- Modify: `params/ImportJobParams.java`
- Test: `.../unit/params/ImportJobParamsExcelTest.java`

> 实施者先 Read `ImportJobParams.java`,沿用其现有 from(...)/字段/getter 风格加两个参数。

- [ ] **Step 1: 写失败测试**

```java
package com.example.filebatchprocessor.unit.params;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.params.ImportJobParams;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportJobParamsExcelTest {

    @Test
    void parsesExcelSheetParams() {
        ImportJobParams p = ImportJobParams.from(Map.of(
                "input.file.name", "x.xlsx",
                "file.format", "EXCEL",
                "excel.sheet.index", "2",
                "excel.sheet.name", "Data"));
        assertEquals(2, p.getExcelSheetIndex());
        assertEquals("Data", p.getExcelSheetName());
    }

    @Test
    void excelSheetDefaultsWhenAbsent() {
        ImportJobParams p = ImportJobParams.from(Map.of("input.file.name", "x.csv", "file.format", "CSV"));
        assertEquals(0, p.getExcelSheetIndex());
        assertEquals(null, p.getExcelSheetName());
    }
}
```

- [ ] **Step 2: 跑确认编译失败**

- [ ] **Step 3: 在 ImportJobParams 加字段 `excelSheetIndex`(int,默认 0)、`excelSheetName`(String,默认 null),在 from(...) 解析 `excel.sheet.index`/`excel.sheet.name`,加 getter。** 沿用现有 from 里取值/转换的写法(注意 jobParameters value 取 String 再解析 int)。

- [ ] **Step 4: 跑测试确认 PASS**

- [ ] **Step 5: 提交**

```bash
git add src/main/java/.../params/ImportJobParams.java src/test/java/.../unit/params/ImportJobParamsExcelTest.java
git commit -m "feat(import): ImportJobParams 加 excel.sheet.index/name"
```

---

## Task 7: 全量回归 + 文档

- [ ] **Step 1: 全 unit-test 回归**

Run: `./mvnw test -Punit-test 2>&1 | grep -E 'Tests run: [0-9]+, Failures.*Skipped: [0-9]+$|BUILD (SUCCESS|FAILURE)' | tail -2`
Expected: Failures: 0, Errors: 0,BUILD SUCCESS(总数 = 改造前基线 + 本特性新增)。CSV/FIXED 行模式既有测试必须仍绿(证明并存不破坏)。

- [ ] **Step 2:(本机有 PG)集成回归**

Run: `./mvnw test -Pintegration-test 2>&1 | tail -3`
Expected: 全绿(导入相关 IT 覆盖)。

- [ ] **Step 3: 文档**

更新 `docs/user-guide/job-configuration-examples.md` 导入段:新增 JSON/Excel 导入示例(file.format=JSON/EXCEL、excel.sheet.index/name、JSON 顶层数组要求、Excel 首行表头列名 id/name/description),并注明文档模式下 checksum/分片语义按 record 序号。提交。

---

## Self-Review 结论

- **Spec 覆盖**:poi-ooxml(T1)/ Document SPI+工厂(T2)/ JSON streaming(T3)/ Excel(T4)/ reader 文档模式路由+分片+checksum+重启(T5)/ ImportJobParams sheet 参数(T6)/ 回归+文档(T7)。spec 各节均有 Task。
- **占位符**:reader/JobConfig 改造处用"现有逻辑原样保留"标注 + 给出新增分支完整代码;实施者须先 Read 原文件再插入(已在 Task 5 顶部明确)。
- **类型一致**:`DocumentRecordReader.open→Iterator<FileRecord>`、`DocumentRecordReaderFactory.{supportsDocument,create}`、`DocumentReadOptions(sheetIndex,sheetName)`、reader 8 参构造器签名跨 Task 一致;`getExcelSheetIndex/Name`(T6 定义,T5 引用)一致。
- **已知前置**:Task 5/6 须先 Read 目标文件全文(FileImportRecordReader、FileImportJobConfig.importReader、ImportJobParams)按现有风格插入,避免破坏行模式与现有 Resource 构造逻辑。
