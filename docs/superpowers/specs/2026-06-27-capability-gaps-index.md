# 能力缺口补齐 — 设计索引(单体定位)

> 日期 2026-06-27。对标分布式批量项目,严格守 file-batch-processor 单租户单体定位补 7 项能力(分 5 个子项目)。不做多租户/SDK/分布式/分布式 trace。

## 子项目与设计稿

| 顺序 | 子项目 | 缺口 | 设计稿 |
|---|---|---|---|
| 1 | 多告警渠道 | #7 | [multi-alert-channels](2026-06-27-multi-alert-channels-design.md) |
| 2 | 导入多格式 | #4 | [import-multi-format](2026-06-27-import-multi-format-design.md) |
| 3 | PGP 解密 + 解压 | #5 | [pgp-decrypt-decompress](2026-06-27-pgp-decrypt-decompress-design.md) |
| 4 | 清单驱动入库 | #3+#6 | [manifest-driven-intake](2026-06-27-manifest-driven-intake-design.md) |
| 5 | 声明式映射 + bundle | #2+#1 | [declarative-mapping-and-bundle](2026-06-27-declarative-mapping-and-bundle-design.md) |

## 构建顺序与依赖

`#7 → #4 → #5 → #3+#6 → #2(P1) → #1(P2)`(独立性递减、风险递增)。

- **#7 / #4 / #5 / #3+#6 彼此独立,可并行实现**(不同包/不同表)。
- **#4 与 #5 都改 `FileImportRecordReader` / `FileImportJobConfig`**:并行时注意同文件冲突,建议串行或一人收口该文件。
- **#5 引入 `batch.io.temp-dir`**;**#4 引入 Document SPI**——互不依赖但同区。
- **#2→#1 动核心模型,放最后单独走**;#2 是 #1 的地基(配置化),#1 复用已有 DagOrchestrator + business_job_instance。
- **#3+#6(清单)与 #1(bundle)边界**:清单只"等齐+对账+放行";bundle 才是"一束作为一个执行单元"。两份设计已各自划清。

## 批准的横切决策

- 新依赖:`starter-mail` + `greenmail`(#7)、BouncyCastle(#5)、POI 仅编译期不可见时补一行(#4,优先复用 Hutool 已带)。
- 核心表 `imported_records_partition` 加 `attributes JSONB`(#2)。
- Flyway 版本顺延:从 V1_38 起(清单 V1_38;映射 V1_38/39;bundle V1_40 —— 实施时按实际落地顺序重新分配避免冲突)。

## 不做(超出单体定位)

多租户隔离(#7 原列)、分布式 trace —— 属另一个分布式多租户项目,本单体不做。

## 状态

5 份设计已产出并经横切决策批准,待逐份转实现计划(writing-plans)。
