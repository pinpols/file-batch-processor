# 声明式映射（地基版）

> 🔴 **重要范围声明**：本版**仅交付配置表、映射引擎与列**，**尚未接入导入链路**。
> `business_key` 多字段化与 config-driven processor/writer 属于后续增量。
> 现有 `importJob` 行为**零变化**，运行时不读取本版任何配置。

## 目的

为后续「按 feed 配置驱动导入」打地基：把「输入文件长什么样、字段怎么映射到目标、做哪些转换」
从硬编码代码迁移到数据库配置（`feed_definition` + `field_mapping`），由纯函数 `MappingEngine` 执行。

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

预留列，用于将来存放「不落固定结构列」的动态映射结果（半结构化属性袋）。
本版只加列，**写入路径尚未接线**。

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

## 当前不做（后续增量）

- 导入链路接线：现有 `importJob` 不读取 `feed_definition` / `field_mapping`，行为不变。
- config-driven processor / writer：按 feed 动态选择处理器与写库逻辑。
- `business_key` 多字段化在写库与去重路径的落地。
- `attributes` JSONB 的实际写入与查询。
