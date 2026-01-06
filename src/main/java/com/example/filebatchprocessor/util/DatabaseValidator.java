package com.example.filebatchprocessor.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库验证工具：检查任务配置表是否创建成功
 */
public class DatabaseValidator {

    public static void main(String[] args) {
        String dbUrl = "jdbc:postgresql://localhost:5432/postgres";
        String dbUser = "postgres";
        String dbPassword = "postgres";

        try {
            Class.forName("org.postgresql.Driver");
            
            try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                System.out.println("✓ 连接到数据库成功");
                
                // 检查 task_definition 表
                System.out.println("\n=== task_definition 表 ===");
                checkTable(connection, "SELECT COUNT(*) as count FROM task_definition");
                checkTable(connection, "SELECT task_id, job_name, priority, enabled FROM task_definition ORDER BY task_id");
                
                // 检查 task_trigger 表
                System.out.println("\n=== task_trigger 表 ===");
                checkTable(connection, "SELECT COUNT(*) as count FROM task_trigger");
                checkTable(connection, "SELECT task_id, trigger_type, cron_expression, fixed_rate_ms FROM task_trigger ORDER BY task_id");
                
                // 检查 task_parameter 表
                System.out.println("\n=== task_parameter 表 ===");
                checkTable(connection, "SELECT COUNT(*) as count FROM task_parameter");
                checkTable(connection, "SELECT task_id, param_name, param_value FROM task_parameter ORDER BY task_id, param_name");
            }
        } catch (Exception e) {
            System.err.println("✗ 验证失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkTable(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            
            int columnCount = rs.getMetaData().getColumnCount();
            
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(rs.getMetaData().getColumnName(i) + "=" + rs.getObject(i));
                    if (i < columnCount) System.out.print(", ");
                }
                System.out.println();
            }
        }
    }
}
