#!/usr/bin/env bash
set -euo pipefail

# 项目清理脚本：删除本地运行产物，不修改源码和测试数据。

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_DIR"

echo "[INFO] 开始清理本地运行产物"

# 清理编译产物
echo "[INFO] 清理编译产物"
rm -rf target/classes target/test-classes

# 清理日志文件
echo "[INFO] 清理日志文件"
find . -name "*.log" -type f -delete 2>/dev/null || true
find . -name "*.pid" -type f -delete 2>/dev/null || true

# 清理临时文件
echo "[INFO] 清理临时文件"
find . -name "*.tmp" -type f -delete 2>/dev/null || true
find . -name "*.cache" -type f -delete 2>/dev/null || true
find . -name ".DS_Store" -delete 2>/dev/null || true
find . -name "Thumbs.db" -delete 2>/dev/null || true

# 清理备份文件
echo "[INFO] 清理备份文件"
find . -name "*.bak" -type f -delete 2>/dev/null || true

# 清理本地导出和自动生成文档
echo "[INFO] 清理本地导出和生成文档"
rm -rf export 2>/dev/null || true
rm -f docs/operations/dag-*-graph.generated.md 2>/dev/null || true
rm -f docs/operations/dag-complex-template.sql 2>/dev/null || true

# 清理 IDE 文件（保留配置）
echo "[INFO] 清理编辑器临时文件"
find . -name "*.swp" -delete 2>/dev/null || true
find . -name "*.swo" -delete 2>/dev/null || true

# 清理 Maven 缓存（可选）
if [[ "${1:-}" == "--deep" ]]; then
    echo "[INFO] 执行 Maven clean"
    mvn clean 2>/dev/null || true
fi

# 显示清理结果
echo "[OK] 清理完成"
echo "  - 编译产物：已清理"
echo "  - 日志文件：已清理"
echo "  - 临时文件：已清理"
echo "  - 本地导出：已清理"
echo "  - 自动文档：已清理"

if [[ "${1:-}" == "--deep" ]]; then
    echo "  - Maven clean：已执行"
fi
