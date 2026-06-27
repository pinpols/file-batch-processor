# 设计:导入多格式(JSON / Excel)

> 缺口 #4。日期 2026-06-27。状态:已批准设计,待写实现计划。

## 目标

让导入支持 JSON(对象数组)、Excel(.xlsx);XML 暂缓。严格单体单租户。

## 现状(file:line)

- `FileImportRecordReader`(`batch/reader/FileImportRecordReader.java`)是 `ItemStreamReader<FileRecord>`,逐行 `readLine()` + 跳表头 + 分片(`(lineCount-1)%shardTotal==shardIndex`)+ 每行 Adler32 checksum,解析委托 `RecordLineParser.parse(String line)`。
- 解析 SPI 纯字符串入参:`spi/RecordLineParser` + `RecordLineParserFactory.create(format,delimiter)`,CSV/FIXED 各一 provider。
- 装配 `batch/config/FileImportJobConfig.importReader`(@StepScope,从 ImportJobParams 取 format/delimiter/shard)。
- 依赖:Jackson 已在 classpath;**Excel 复用 hutool-all 传递带入的 POI**(导出侧 FileExportService 已用 ExcelUtil);唯一不确定是 poi-ooxml 编译期可见性(实现时 `mvn dependency:tree | grep poi` 确认,缺则补一行,不引 easyexcel)。

核心矛盾:SPI 是行模型(String line),JSON 数组/Excel 工作簿是文档模型,无法逐行喂。

## 方案

新增 `DocumentRecordReaderProvider` SPI 与现有 line-parser **并存**,`FileImportRecordReader` 按 fileFormat 路由:行模式(CSV/FIXED)保持现状不动;文档模式(JSON/EXCEL)在 open 时把整个 Resource 解析成 `Iterator<FileRecord>`,read() 从迭代器逐个 next()。

**否决备选**:把 JSON/Excel 硬塞进 RecordLineParser(String)(丢类型、还得文档级解析、白绕);每格式独立 ItemReader Bean(复制 checksum/重启/分片样板,重复度高)。

## 范围边界

**做**:JSON(顶层对象数组)、Excel .xlsx 第一个 sheet(或参数 excel.sheet.index/name 指定),首行表头按列名映射到 FileRecord(id/name/description,大小写不敏感)。JSON 走 Jackson streaming(JsonParser 逐元素,不全量 readValue)。

**不做(YAGNI)**:XML(预留 supports("XML") 扩展位);Excel 多 sheet 合并;Excel 流式(全量 DOM,文档化"建议 ≤ 数万行",大文件二期上 SAX XSSFReader);引 easyexcel。

## 组件/接口

```
batch/reader/spi/
  DocumentRecordReaderProvider(supports(format), create(DocumentReadOptions))
  DocumentRecordReader extends Closeable (Iterator<FileRecord> open(Resource))
  DocumentReadOptions(record: Integer sheetIndex, String sheetName)
  DocumentRecordReaderFactory(@Component, 注入 List<provider>, create + supportsDocument)
  JsonDocumentRecordReader + Provider
  ExcelDocumentRecordReader + Provider
```

衔接 `FileImportRecordReader`:构造期 `documentFactory.supportsDocument(format)` 判模式;文档模式 open() 拿迭代器;read() 从迭代器 next() 并递增 recordSeq,分片改 `recordSeq%shardTotal==shardIndex`,checksum 改喂 record 规范化字节(语义从"文件字节"变"记录内容",文档化),重启改按 recordSeq 跳过 N 条。下游 writer/dedup 消费 FileRecord 不受影响。

`ImportJobParams` 加 excel.sheet.index/name;可选 format 白名单校验。`FileImportJobConfig.importReader` 注入 DocumentRecordReaderFactory 透传。

## 文件清单

新增 `batch/reader/spi/` 7 个(SPI/工厂/JSON/Excel)。改:`FileImportRecordReader`(文档模式分支)、`FileImportJobConfig`、`ImportJobParams`、可能 `pom.xml`(poi-ooxml 一行)。

## 风险

1. 大 Excel 全量 DOM OOM(已界定边界 + 二期 SAX);JSON streaming 已规避。
2. Excel 单元格类型(数值 1.0 / 日期序列号)→ ExcelDocumentRecordReader 显式按目标字段转换 + 脏数据走 parseErrorCount。
3. 分片对文档格式语义变为 record 序号取模;Excel 分片会重复全量读 N 遍(单机可接受)。
4. checksum 语义跨格式不可比(文档化)。
5. 重启恢复按 recordSeq 跳过,易 off-by-one(尤其 Excel 表头),测试覆盖。

## 测试计划

- 单测:JsonDocumentRecordReaderTest(N 条/字段映射/类型/空数组/脏元素跳过);ExcelDocumentRecordReaderTest(用 Hutool ExcelWriter 测试内现写 .xlsx 再读,验表头/列映射/1.0→1L/空单元格/sheet 选择);FileImportRecordReaderDocumentModeTest(迭代到 null/分片/重启/计数)。
- 回归:现有 CSV/FIXED 行模式测试保持绿(证明并存不破坏)。
- 测试依赖:starter-test/Hutool/Jackson 均已有,无需新增;Excel 样本代码生成不引二进制 fixture。
