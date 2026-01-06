package com.example.filebatchprocessor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 数据库初始化监听器
 * 在应用启动时执行 DDL SQL 脚本，初始化任务配置表
 */
@Slf4j
@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDatabase() {
        try {
            log.info("开始初始化数据库任务配置表...");
            
            ClassPathResource resource = new ClassPathResource("db/migration/V1_0__init_task_config.sql");
            if (!resource.exists()) {
                log.warn("DDL 脚本文件不存在，跳过自动初始化");
                return;
            }

            StringBuilder scriptContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    scriptContent.append(line).append("\n");
                }
            }

            // 按 ; 分割 SQL 语句
            String[] statements = scriptContent.toString().split(";");
            int executedCount = 0;

            for (String statement : statements) {
                String sql = statement.trim();
                
                // 忽略注释和空行
                if (sql.isEmpty() || sql.startsWith("--")) {
                    continue;
                }

                try {
                    log.debug("执行 SQL: {}", sql.substring(0, Math.min(80, sql.length())));
                    jdbcTemplate.execute(sql);
                    executedCount++;
                } catch (Exception e) {
                    log.warn("SQL 执行出现警告（通常为幂等性检查）: {}", e.getMessage());
                    // 继续执行其他语句，因为 ON CONFLICT DO NOTHING 可能导致警告
                }
            }

            log.info("✓ 数据库初始化完成！共执行 {} 条 SQL 语句", executedCount);
        } catch (Exception e) {
            log.error("✗ 数据库初始化失败", e);
        }
    }
}
