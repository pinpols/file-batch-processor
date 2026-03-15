#!/usr/bin/env bash
set -euo pipefail

# 项目清理脚本
# 用于清理不必要的文件和保持项目整洁

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

echo "🧹 开始清理项目..."

# 清理编译产物
echo "📦 清理编译产物..."
rm -rf target/classes target/test-classes
find target -name "*.jar" -not -name "*.jar" -delete 2>/dev/null || true

# 清理日志文件
echo "📝 清理日志文件..."
find . -name "*.log" -type f -delete 2>/dev/null || true
find . -name "*.pid" -type f -delete 2>/dev/null || true

# 清理临时文件
echo "🗂️ 清理临时文件..."
find . -name "*.tmp" -type f -delete 2>/dev/null || true
find . -name "*.cache" -type f -delete 2>/dev/null || true
find . -name ".DS_Store" -delete 2>/dev/null || true
find . -name "Thumbs.db" -delete 2>/dev/null || true

# 清理备份文件
echo "💾 清理备份文件..."
find . -name "*.bak" -type f -delete 2>/dev/null || true

# 清理自动生成的文档
echo "📚 清理自动生成的文档..."
rm -f docs/todo_analysis_plans/dag-*-graph.generated.md 2>/dev/null || true
rm -f docs/todo_analysis_plans/dag-complex-template.sql 2>/dev/null || true

# 清理 IDE 文件（保留配置）
echo "💻 清理 IDE 文件..."
find . -name "*.swp" -delete 2>/dev/null || true
find . -name "*.swo" -delete 2>/dev/null || true

# 清理 Maven 缓存（可选）
if [[ "${1}" == "--deep" ]]; then
    echo "🧹 深度清理 Maven 缓存..."
    rm -rf .mvn/wrapper/maven-wrapper.jar 2>/dev/null || true
    mvn clean 2>/dev/null || true
fi

# 显示清理结果
echo "✅ 项目清理完成！"
echo ""
echo "📊 清理统计："
echo "  - 编译产物：已清理"
echo "  - 日志文件：已清理" 
echo "  - 临时文件：已清理"
echo "  - 自动文档：已清理"

if [[ "${1}" == "--deep" ]]; then
    echo "  - Maven 缓存：已深度清理"
fi

echo ""
echo "💡 提示："
echo "  - 使用 './scripts/cleanup.sh --deep' 进行深度清理"
echo "  - 查看 docs/other/PROJECT_STRUCTURE.md 了解项目结构"
echo "  - 使用 './scripts/local/start-local.sh' 启动开发环境"
