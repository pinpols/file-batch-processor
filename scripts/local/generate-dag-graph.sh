#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# shellcheck source=../lib/env-common.sh
source "${PROJECT_DIR}/scripts/lib/env-common.sh"

DAG_ID="${DAG_ID:-dag-complex-sample}"
OUTPUT_FILE="${OUTPUT_FILE:-${PROJECT_DIR}/docs/operations/dag-${DAG_ID}-graph.generated.md}"

DB_URL="${SPRING_DATASOURCE_URL}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-postgres}"
DB_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-postgres}"
POSTGRES_JAR="${POSTGRES_JAR:-$HOME/.m2/repository/org/postgresql/postgresql/42.7.11/postgresql-42.7.11.jar}"

if [[ ! -f "${POSTGRES_JAR}" ]]; then
  echo "[ERROR] PostgreSQL JDBC jar not found: ${POSTGRES_JAR}" >&2
  exit 1
fi

tmp_jshell="$(mktemp)"
trap 'rm -f "${tmp_jshell}"' EXIT

cat > "${tmp_jshell}" <<'EOF'
import java.sql.*;
import java.nio.file.*;
import java.util.*;

String dagId = System.getenv().getOrDefault("DAG_ID", "dag-complex-sample");
String outFile = System.getenv().get("OUTPUT_FILE");
String dbUrl = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/file_batch");
String dbUser = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres");
String dbPassword = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "postgres");

Class.forName("org.postgresql.Driver");

Map<String, String> labels = new LinkedHashMap<>();
List<String[]> edges = new ArrayList<>();

try (Connection c = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
    try (PreparedStatement ps = c.prepareStatement(
            "select n.task_id, coalesce(d.job_name, n.task_id) as label " +
            "from dag_node n left join task_definition d on d.task_id=n.task_id " +
            "where n.dag_id=? and n.enabled=true order by n.node_order, n.id")) {
        ps.setString(1, dagId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                labels.put(rs.getString("task_id"), rs.getString("label"));
            }
        }
    }

    if (labels.isEmpty()) {
        throw new IllegalStateException("No enabled DAG nodes found for dagId=" + dagId);
    }

    String inClause = String.join(",", Collections.nCopies(labels.size(), "?"));
    String sql = "select task_id, depends_on_task_id from task_dependency " +
                 "where task_id in (" + inClause + ") and depends_on_task_id in (" + inClause + ")";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
        int idx = 1;
        for (String t : labels.keySet()) ps.setString(idx++, t);
        for (String t : labels.keySet()) ps.setString(idx++, t);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                edges.add(new String[]{rs.getString("depends_on_task_id"), rs.getString("task_id")});
            }
        }
    }
}

StringBuilder md = new StringBuilder();
md.append("# DAG 任务依赖图\n");
md.append("> 本文件由 scripts/local/generate-dag-graph.sh 生成，不应提交到仓库。\n\n");
md.append("DAG ID: `").append(dagId).append("`\n\n");
md.append("```mermaid\n");
md.append("flowchart TD\n");
for (Map.Entry<String,String> e : labels.entrySet()) {
    String node = e.getKey().replace('-', '_');
    String label = e.getKey() + " (" + e.getValue() + ")";
    md.append("    ").append(node).append("[\"").append(label).append("\"]\n");
}
for (String[] edge : edges) {
    String from = edge[0].replace('-', '_');
    String to = edge[1].replace('-', '_');
    md.append("    ").append(from).append(" --> ").append(to).append("\n");
}
md.append("```\n");

Path out = Path.of(outFile);
Files.createDirectories(out.getParent());
Files.writeString(out, md.toString());
System.out.println("WROTE=" + outFile);
/exit
EOF

echo "[INFO] generating DAG graph for dagId=${DAG_ID}"
DAG_ID="${DAG_ID}" OUTPUT_FILE="${OUTPUT_FILE}" \
SPRING_DATASOURCE_URL="${DB_URL}" SPRING_DATASOURCE_USERNAME="${DB_USER}" SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}" \
jshell --class-path "${POSTGRES_JAR}" "${tmp_jshell}" >/tmp/generate_dag_graph.log

grep "WROTE=" /tmp/generate_dag_graph.log || {
  echo "[ERROR] generation failed. see /tmp/generate_dag_graph.log" >&2
  cat /tmp/generate_dag_graph.log >&2
  exit 1
}

echo "[OK] generated: ${OUTPUT_FILE}"
