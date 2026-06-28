# 作业配置示例

本文档给出常见导入作业的 `task_parameter` 配置示例。参数键的完整契约见
`docs/api/jobs-params-contract.md`，表结构见 `docs/architecture/task-configuration-schema.md`。

## 导入文件格式

导入作业通过 `file.format` 参数选择解析方式，分为两大类语义：

| 类别 | `file.format` 取值 | 解析方式 |
| --- | --- | --- |
| 行格式 | `CSV`、`FIXED` | 逐行解析（`RecordLineParser`），可配 `file.delimiter` |
| 文档格式 | `JSON`、`EXCEL`（含 `XLSX`） | 整文件解析为记录流（`DocumentRecordReader`） |

字段映射统一为 `FileRecord` 的三列：`id`（可选）、`name`、`description`。

### CSV 导入（行格式，默认）

```sql
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('import-csv', 'input.file.name', '${user.dir}/src/main/resources/data/sample.csv', 'STRING', '导入文件路径');

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('import-csv', 'file.format', 'CSV', 'STRING', '文件格式');

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('import-csv', 'file.delimiter', ',', 'STRING', '字段分隔符');
```

### JSON 导入（文档格式）

要点：

- 输入文件必须是**顶层对象数组**：`[{"id":1,"name":"a","description":"x"}, ...]`。
- 每个对象的 `id`（可选）/`name`/`description` 字段映射到 `FileRecord`。
- 走 Jackson streaming 解析，逐条产出，不一次性载入整个数组。

```sql
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('import-json', 'input.file.name', '${user.dir}/src/main/resources/data/sample.json', 'STRING', '导入文件路径');

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('import-json', 'file.format', 'JSON', 'STRING', '文件格式');
```

### Excel 导入（文档格式）

要点：

- 仅支持 `.xlsx`，**首行为表头**，列名 `id`/`name`/`description`（大小写不敏感）映射到 `FileRecord`。
- 可选 `excel.sheet.index`（默认 `0`）选第几个 sheet；可选 `excel.sheet.name` 按名选 sheet，
  **`excel.sheet.name` 优先于 `excel.sheet.index`**。
- 基于 Hutool/POI 全量读入。受限于全量加载，建议单文件 **≤ 数万行**；超大文件的流式读取留待二期。

```sql
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('import-excel', 'input.file.name', '${user.dir}/src/main/resources/data/sample.xlsx', 'STRING', '导入文件路径');

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('import-excel', 'file.format', 'EXCEL', 'STRING', '文件格式（EXCEL/XLSX）');

-- 可选：按下标选 sheet（默认 0）
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('import-excel', 'excel.sheet.index', '0', 'INT', 'sheet 下标，默认 0');

-- 可选：按名选 sheet（优先于 excel.sheet.index）
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('import-excel', 'excel.sheet.name', 'Sheet1', 'STRING', 'sheet 名称，优先于下标');
```

## 文档格式与行格式的语义差异

文档格式（JSON/Excel）与行格式（CSV/定长）在分片与校验上语义不同，配置时需注意：

- **分片**：文档格式按 **record 序号取模**（`序号 % shard.total == shard.index`）选取本分片记录；
  行格式按文件行/字节切分。
- **checksum**：文档格式按 **记录内容**（`id|name|description` 规范化字节）累加；
  行格式按**文件字节**计算。
- **重启恢复**：文档格式跳过前 N 条 record（N 为已处理记录数）继续。

因此同一份逻辑数据切换 `file.format` 时，分片结果与 checksum 不可跨格式直接比对。

## 加密 / 压缩 / 清单驱动

- **加密(PGP)+ 压缩(.gz/.zip)文件导入**：`input.file.encrypted`（true/false，缺省按 `.pgp/.gpg` 后缀判定）、`input.file.compression`（gz/zip/none，缺省按后缀）；私钥 `batch.pgp.private-key-path` + `BATCH_PGP_PASSPHRASE`(env)。详见 [encrypted-compressed-intake](../operations/encrypted-compressed-intake.md)。
- **清单(manifest)驱动入库**：`.manifest.json` 控制文件列出期望文件 + 条数/MD5，等组到齐对账通过才放行(`batch.file.reception.group.enabled`，默认关)。详见 [manifest-driven-intake](../operations/manifest-driven-intake.md)。

## 安全相关配置(导入/导出/分发)

| 项 | 配置 | 说明 |
|---|---|---|
| 导出 SQL 白名单 | `export.sql` 参数 | 仅接受单条只读 SELECT；禁 DML/DDL/分号/注释/危险函数(`pg_read_file`/`dblink` 等) |
| 路径穿越防护 | `batch.io.input-base-dir` / `output-base-dir` | 配置后导入/导出文件限定在基目录内；留空也拒绝 `..` 逃逸 |
| SFTP 主机密钥 | `sftp.known-hosts-path` | 默认 fail-closed(加载 known_hosts，缺失即拒连)；`sftp.insecure-skip-host-key-check=true` 仅 dev |
| 分发 SSRF | `distribution.allowed-hosts` | HTTP/FTP 目标白名单;`distribution.block-internal-targets=true` 额外拦内网 |
| 质量门硬闸门 | `quality.enforce`(job 参数)/`quality.enforce-default` | 默认硬阻断；需兼容旧软降级时按作业设 `quality.enforce=false` |
| 运维端点鉴权 | `ops.security.*`(viewer/operator/admin) | 破坏性端点限 ADMIN;生产弱口令 fail-fast |

## 多告警渠道 / 声明式映射

- 告警渠道(webhook/email/IM)配置见 [alerting-channels](../operations/alerting-channels.md)。
- 声明式字段映射**已接入(feedId 路由)**：job 参数带 `feedId` 即走 feed 路径,不带则默认导入路径不变,见 [declarative-mapping](../operations/declarative-mapping.md)。
