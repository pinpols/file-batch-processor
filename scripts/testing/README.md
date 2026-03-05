# 📊 测试数据中心

> 完整的测试数据集、场景和验证工具

## 🎯 数据集概览

### 📁 CSV 数据文件
| 文件名 | 描述 | 记录数 | 场景 |
|------|------|----------|------|
| [import_success.csv](import_success.csv) | 正常导入数据 | 3 | 基础功能测试 |
| [import_with_parse_errors.csv](import_with_parse_errors.csv) | 包含解析错误 | 5 | 错误处理测试 |
| [import_idempotent.csv](import_idempotent.csv) | 重复业务键 | 2 | 幂等性测试 |
| [large_dataset_financial.csv](large_dataset_financial.csv) | 大数据集 | 10 | 性能测试 |
| [chinese_dataset_financial.csv](chinese_dataset_financial.csv) | 中文数据集 | 10 | 国际化测试 |

### 🗃️ SQL 种子文件
| 文件名 | 描述 | 数据类型 | 用途 |
|------|------|----------|------|
| [seed_imported_records.sql](seed_imported_records.sql) | 基础导入记录 | 业务数据 | 基础测试 |
| [seed_trace_and_dlq.sql](seed_trace_and_dlq.sql) | 追踪和死信队列 | 链路数据 | 追踪测试 |
| [seed_reconcile_mismatch.sql](seed_reconcile_mismatch.sql) | 对账不匹配 | 对账数据 | 对账测试 |
| [seed_enhanced_test_data.sql](seed_enhanced_test_data.sql) | 综合测试数据 | 完整场景 | 集成测试 |

## 🧪 测试场景

### 1. 基础功能测试
**目标**：验证核心导入导出功能

**数据文件**：
- `import_success.csv` - 正常数据导入
- `seed_imported_records.sql` - 基础数据

**验证点**：
- ✅ 数据正确导入
- ✅ 字段映射正确
- ✅ 业务键唯一性
- ✅ 时间戳记录

### 2. 错误处理测试
**目标**：验证异常数据处理能力

**数据文件**：
- `import_with_parse_errors.csv` - 解析错误数据
- `seed_trace_and_dlq.sql` - 死信队列数据

**验证点**：
- ✅ 错误数据正确识别
- ✅ 死信队列记录
- ✅ 错误统计准确
- ✅ 部分成功处理

### 3. 幂等性测试
**目标**：验证重复数据处理

**数据文件**：
- `import_idempotent.csv` - 重复业务键数据

**验证点**：
- ✅ 重复数据跳过
- ✅ 原数据保持不变
- ✅ 幂等性日志记录
- ✅ 去重策略正确

### 4. 性能测试
**目标**：验证大数据量处理

**数据文件**：
- `large_dataset_financial.csv` - 大数据集
- `seed_enhanced_test_data.sql` - 完整场景数据

**验证点**：
- ✅ 大数据量处理能力
- ✅ 内存使用合理
- ✅ 处理时间可接受
- ✅ 分片策略有效

### 5. 国际化测试
**目标**：验证多语言支持

**数据文件**：
- `chinese_dataset_financial.csv` - 中文数据

**验证点**：
- ✅ 中文字符正确处理
- ✅ 编码转换正确
- ✅ 排序规则正确
- ✅ 搜索功能正常

### 6. 数据对账测试
**目标**：验证数据一致性

**数据文件**：
- `seed_reconcile_mismatch.sql` - 对账不匹配数据

**验证点**：
- ✅ 对账差异识别
- ✅ 不匹配报告生成
- ✅ 修复建议提供
- ✅ 对账日志完整

## 🛠️ 数据管理工具

### 初始化脚本
```bash
# 基础测试环境
./scripts/testdata/init-test-environment.sh

# 增强测试环境
./scripts/testdata/init-test-environment.sh --enhanced

# 自定义数据加载
./scripts/testdata/load-testdata-postgres.sh seed_imported_records.sql
```

### 数据验证
```bash
# 验证数据完整性
./scripts/testdata/verify-data.sh

# 生成测试报告
./scripts/testdata/generate-test-report.sh

# 清理测试数据
./scripts/testdata/cleanup-testdata.sh
```

### 数据生成
```bash
# 生成测试数据
./scripts/testdata/generate-testdata.sh --count 1000 --type financial

# 生成压力测试数据
./scripts/testdata/generate-stress-data.sh --size 10MB

# 生成边界测试数据
./scripts/testdata/generate-boundary-data.sh
```

## 📋 数据格式规范

### CSV 格式
```csv
id,name,description,category,amount,status,created_at
1001,张三,正常交易记录,finance,1500.50,active,2026-03-01 10:30:00
```

**字段说明**：
- `id`：唯一标识符，数字类型
- `name`：姓名，字符串类型，支持中文
- `description`：描述，字符串类型
- `category`：分类，字符串类型
- `amount`：金额，浮点数类型
- `status`：状态，枚举类型（active/pending/failed/completed）
- `created_at`：创建时间，时间戳格式

### SQL 格式
```sql
-- 插入导入记录
INSERT INTO imported_records (business_key, name, description, batch_date, created_at, updated_at) VALUES
    ('FIN001:2026-03-01', '张三', '正常交易记录', '2026-03-01', '2026-03-01 10:30:00', '2026-03-01 10:30:00');
```

**数据表结构**：
- `imported_records`：导入记录主表
- `imported_record_partitioned`：分区导入记录
- `task_execution_state`：任务执行状态
- `dlq_record`：死信队列记录
- `record_trace`：记录追踪
- `quality_gate_results`：质量门禁结果

## 🧪 测试用例

### 单元测试数据
```java
@Test
void testImportSuccessData() {
    // 使用 import_success.csv
    List<FileRecord> records = importService.import("import_success.csv");
    assertEquals(3, records.size());
}
```

### 集成测试数据
```java
@Test
void testEndToEndWorkflow() {
    // 使用 seed_enhanced_test_data.sql
    testDataService.loadEnhancedData();
    workflowService.executeCompleteWorkflow();
    assertWorkflowSuccess();
}
```

### 性能测试数据
```java
@Test
void testLargeDatasetPerformance() {
    // 使用 large_dataset_financial.csv
    long startTime = System.currentTimeMillis();
    importService.import("large_dataset_financial.csv");
    long duration = System.currentTimeMillis() - startTime;
    assertTrue(duration < 30000); // 30秒内完成
}
```

## 📊 数据质量检查

### 完整性检查
- **记录数验证**：预期记录数 vs 实际记录数
- **字段完整性**：必填字段是否都有值
- **数据类型验证**：字段类型是否正确
- **约束检查**：唯一性、外键约束

### 一致性检查
- **业务规则验证**：是否符合业务逻辑
- **数据关联性**：关联表数据一致性
- **时间戳一致性**：创建/更新时间逻辑
- **状态流转验证**：状态变化合理性

### 准确性检查
- **数值范围验证**：金额、数量等数值范围
- **格式验证**：日期、编码等格式正确性
- **枚举值验证**：状态字段枚举值正确
- **特殊字符处理**：特殊字符、转义字符处理

## 🔧 自定义测试数据

### 添加新场景
1. **创建 CSV 文件**：按照格式规范创建
2. **编写 SQL 脚本**：创建对应的种子数据
3. **更新测试用例**：编写相应的测试代码
4. **验证数据质量**：运行数据质量检查
5. **更新文档**：更新测试数据文档

### 数据生成模板
```python
#!/usr/bin/env python3
import csv
import random
from datetime import datetime

def generate_test_data(count, output_file):
    """生成测试数据"""
    with open(output_file, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(['id', 'name', 'description', 'category', 'amount', 'status', 'created_at'])
        
        for i in range(1, count + 1):
            row = [
                i,
                f'测试用户{i}',
                f'测试描述{i}',
                random.choice(['finance', 'hr', 'it']),
                round(random.uniform(100, 10000), 2),
                random.choice(['active', 'pending', 'completed']),
                datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            ]
            writer.writerow(row)

if __name__ == '__main__':
    generate_test_data(1000, 'custom_test_data.csv')
```

## 📞 数据问题反馈

### 数据质量问题
- **数据错误**：提交 Issue 标记 `data-quality`
- **格式问题**：提交 Issue 标记 `data-format`
- **缺失场景**：提交 Issue 标记 `missing-scenario`

### 新场景需求
- **业务场景**：提交 Issue 标记 `new-scenario`
- **数据类型**：提交 Issue 标记 `new-data-type`
- **测试用例**：提交 Issue 标记 `new-test-case`

## 📅 数据更新记录

### 版本历史
- **v2.0.0**：增强测试数据集，添加综合场景
- **v1.5.0**：新增中文数据集和国际化测试
- **v1.0.0**：基础测试数据集

### 最近更新
- **2026-03-05**：新增大数据集和性能测试数据
- **2026-03-05**：添加中文数据集支持
- **2026-03-05**：完善错误处理和边界测试数据

---

**📊 所有测试数据都经过严格验证，确保测试覆盖率和准确性**
