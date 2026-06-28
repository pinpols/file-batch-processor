# 声明式映射

> ✅ **已接入导入链路（feedId 路由）**：job 参数带 `feedId` 即触发 feed 路径，
> 按该 feed 的 `field_mapping` 声明式映射 CSV 原始列到 `name`/`description` 主列与 `attributes`（JSONB），
> `business_key` 按 `feed_definition.business_key_fields` 配置（空则退回 `name:batchDate`）。
> **不带 `feedId` 时默认导入路径完全不变**（零行为变化）。
> 守门回归见 `DeclarativeMappingWiringIT`：默认路径 vs 对照 feed `default-csv` 字节级逐行一致。

## 目的

把「输入文件长什么样、字段怎么映射到目标、做哪些转换」从硬编码代码迁移到数据库配置
（`feed_definition` + `field_mapping`），由纯函数 `MappingEngine` 执行，运行时按 `feedId` 路由。

## 表结构（Flyway V1_39）

### `feed_definition`

一个 feed 对应一类输入文件及其格式 / 目标表 / 业务键。

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `feed_id` | VARCHAR(100) PK | feed 唯一标识 |
| `feed_name` | VARCHAR(200) | 可读名称 |
| `format` | VARCHAR(20) NOT NULL，默认 `CSV` | 文件格式 |
| `delimiter` | VARCHAR(8)，默认 `,` | 分隔符 |
| `has_header` | BOOLEAN NOT NULL，默认 `TRUE` | 首行是否表头 |
| `target_table` | VARCHAR(100) NOT NULL，默认 `imported_records_partition` | 目标表 |
| `business_key_fields` | VARCHAR(500) | 业务键字段（逗号分隔，多字段化为后续增量） |
| `enabled` | BOOLEAN NOT NULL，默认 `TRUE` | 是否启用 |
| `created_at` / `updated_at` | TIMESTAMP NOT NULL | 审计时间 |

### `field_mapping`

单条字段映射规则，按 `order_no` 升序应用。`(feed_id, target_field)` 唯一。

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGSERIAL PK | 主键 |
| `feed_id` | VARCHAR(100) NOT NULL，FK→`feed_definition` | 所属 feed |
| `source_column` | VARCHAR(200) NOT NULL | 源列名 |
| `target_field` | VARCHAR(200) NOT NULL | 目标字段名 |
| `transform_op` | VARCHAR(20) NOT NULL，默认 `NONE` | 转换算子（见下） |
| `transform_arg` | VARCHAR(200) | 算子参数（如日期格式） |
| `required` | BOOLEAN NOT NULL，默认 `FALSE` | 是否必填 |
| `order_no` | INTEGER NOT NULL，默认 `0` | 应用顺序 |
| `enabled` | BOOLEAN NOT NULL，默认 `TRUE` | 是否启用 |

### `imported_records_partition.attributes`（JSONB，V1_39 新增列）

存放「不落固定结构列」的动态映射结果（半结构化属性袋）。
feed 路径下，`target_field` 为 `name`/`description` 的映射落到主列，**其余 `target_field` 落到 `attributes` JSONB**，
回读为 `Map<String,Object>`。

## 映射引擎 `MappingEngine`

无副作用纯函数：输入映射规则列表 + 源行（列名→值），输出目标字段 Map。支持 6 个算子 + 必填校验。

| 算子 | 行为 |
| --- | --- |
| `NONE` | 原值透传（转字符串） |
| `TRIM` | 去首尾空白 |
| `UPPER` | 转大写（`Locale.ROOT`） |
| `LOWER` | 转小写（`Locale.ROOT`） |
| `DATE_FORMAT` | 按 `transform_arg` 给定源格式解析日期，输出 ISO `yyyy-MM-dd`；`arg` 缺省按 `yyyy-MM-dd` 解析 |
| `DEFAULT` | 源值为 null / 空白时取 `transform_arg` 作为默认值，否则取源值 |

**必填校验**：`required=true` 的字段若映射后为 `null` 或空白，抛 `IllegalArgumentException`（携带 `target_field`）。

## 如何触发 feed 路径

在导入 job 参数中加 `feedId` 即走 feed 路径，其余参数不变：

```text
feedId=default-csv
batchDate=2026-07-01
file.format=CSV
file.delimiter=,
input.file.name=/path/to/input.csv
```

不带 `feedId` 则走默认导入路径（行为完全不变）。feed 模式按文件首行自动探测列名，
`source_column` 即对应 CSV 表头列名。

## 最小配置示例（SQL）

对照 feed `default-csv`（复刻默认语义,见迁移 V1_40）：

```sql
INSERT INTO feed_definition (feed_id, feed_name, format, delimiter, has_header,
                             target_table, business_key_fields, enabled)
VALUES ('default-csv', 'Default CSV (parity feed)', 'CSV', ',', TRUE,
        'imported_records_partition', NULL, TRUE);

INSERT INTO field_mapping (feed_id, source_column, target_field, transform_op, required, order_no, enabled)
VALUES ('default-csv', 'name',        'name',        'UPPER', TRUE,  1, TRUE),
       ('default-csv', 'description', 'description', 'NONE',  FALSE, 2, TRUE);
```

两列映射到 `name` 主列 + `attributes` JSONB 的例子（`c_cat` 落 `attributes.category`）：

```sql
INSERT INTO feed_definition (feed_id, feed_name, format, delimiter, has_header,
                             target_table, business_key_fields, enabled)
VALUES ('demo-attrs', 'Demo attrs feed', 'CSV', ',', TRUE,
        'imported_records_partition', NULL, TRUE);

INSERT INTO field_mapping (feed_id, source_column, target_field, transform_op, required, order_no, enabled)
VALUES ('demo-attrs', 'c_name', 'name',     'UPPER', TRUE,  1, TRUE),
       ('demo-attrs', 'c_cat',  'category', 'NONE',  FALSE, 2, TRUE);
```

`business_key_fields=NULL` 时退回 `name:batchDate`（name 已经过 transform）。

## 当前限制（后续增量）

- **仅支持 CSV 行格式 feed**；JSON / Excel feed 后置。
- `business_key` 多字段化（`business_key_fields` 配多列）已接线生效，空值退回 `name:batchDate`。
