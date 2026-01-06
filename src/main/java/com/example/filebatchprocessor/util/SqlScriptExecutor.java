package com.example.filebatchprocessor.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * SQL 脚本执行工具
 * 用于在应用启动时初始化数据库结构
 */
public class SqlScriptExecutor {

    public static void main(String[] args) {
        String dbUrl = "jdbc:postgresql://localhost:5432/postgres";
        String dbUser = "postgres";
        String dbPassword = "postgres";
        String scriptPath = "src/main/resources/db/migration/V1_0__init_task_config.sql";

        try {
            executeSqlScript(dbUrl, dbUser, dbPassword, scriptPath);
            System.out.println("✓ SQL 脚本执行成功！");
        } catch (Exception e) {
            System.err.println("✗ SQL 脚本执行失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 执行 SQL 脚本文件
     */
    public static void executeSqlScript(String dbUrl, String dbUser, String dbPassword, String scriptPath) throws Exception {
        // 读取脚本文件
        String scriptContent = new String(Files.readAllBytes(Paths.get(scriptPath)), StandardCharsets.UTF_8);
        
        // 分割 SQL 语句（按 ; 分割，忽略注释）
        String[] sqlStatements = splitSqlStatements(scriptContent);
        
        // 连接数据库并执行
        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            System.out.println("连接到数据库：" + dbUrl);
            
            try (Statement statement = connection.createStatement()) {
                int executedCount = 0;
                for (String sql : sqlStatements) {
                    sql = sql.trim();
                    if (sql.isEmpty()) {
                        continue;
                    }
                    
                    try {
                        System.out.println("执行：" + sql.substring(0, Math.min(60, sql.length())) + "...");
                        statement.execute(sql);
                        executedCount++;
                    } catch (Exception e) {
                        System.err.println("  执行失败：" + e.getMessage());
                        // 继续执行其他语句
                    }
                }
                
                System.out.println("\n✓ 共执行 " + executedCount + " 条 SQL 语句");
            }
        }
    }

    /**
     * 分割 SQL 语句（简单实现，按 ; 分割，并忽略注释行）
     */
    private static String[] splitSqlStatements(String scriptContent) {
        StringBuilder currentStatement = new StringBuilder();
        java.util.List<String> statements = new java.util.ArrayList<>();
        
        for (String line : scriptContent.split("\n")) {
            line = line.trim();
            
            // 忽略注释和空行
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            
            currentStatement.append(line).append(" ");
            
            // 当行以 ; 结尾时，完成一个 SQL 语句
            if (line.endsWith(";")) {
                statements.add(currentStatement.toString().trim());
                currentStatement = new StringBuilder();
            }
        }
        
        return statements.toArray(new String[0]);
    }
}
