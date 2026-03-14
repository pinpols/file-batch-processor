package com.example.filebatchprocessor.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "quartz.debug.enabled", havingValue = "true", matchIfMissing = false)
public class QuartzDebugTool implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Quartz数据库调试信息 ===");
        
        // 查询所有Quartz表
        String[] tables = {
            "qrtz_job_details", 
            "qrtz_triggers", 
            "qrtz_simple_triggers", 
            "qrtz_cron_triggers",
            "qrtz_fired_triggers",
            "qrtz_scheduler_state"
        };
        
        for (String table : tables) {
            try {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                System.out.println(table + ": " + count + " 条记录");
            } catch (Exception e) {
                System.out.println(table + ": 表不存在或查询失败 - " + e.getMessage());
            }
        }

        // 查询具体的触发器信息
        System.out.println("\n=== 触发器详细信息 ===");
        try {
            List<Map<String, Object>> triggers = jdbcTemplate.queryForList(
                "SELECT trigger_name, trigger_group, trigger_state, next_fire_time FROM qrtz_triggers ORDER BY trigger_name");
            
            for (Map<String, Object> trigger : triggers) {
                System.out.println("触发器: " + trigger.get("trigger_name") + 
                    " | 组: " + trigger.get("trigger_group") + 
                    " | 状态: " + trigger.get("trigger_state") + 
                    " | 下次执行: " + trigger.get("next_fire_time"));
            }
        } catch (Exception e) {
            System.out.println("查询触发器失败: " + e.getMessage());
        }

        // 查询作业详情
        System.out.println("\n=== 作业详细信息 ===");
        try {
            List<Map<String, Object>> jobs = jdbcTemplate.queryForList(
                "SELECT job_name, job_group, job_class_name FROM qrtz_job_details ORDER BY job_name");
            
            for (Map<String, Object> job : jobs) {
                System.out.println("作业: " + job.get("job_name") + 
                    " | 组: " + job.get("job_group") + 
                    " | 类: " + job.get("job_class_name"));
            }
        } catch (Exception e) {
            System.out.println("查询作业失败: " + e.getMessage());
        }

        // 清理孤立的触发器记录
        System.out.println("\n=== 清理孤立记录 ===");
        try {
            // 删除没有对应作业的触发器
            int deletedTriggers = jdbcTemplate.update(
                "DELETE FROM qrtz_triggers WHERE (job_name, job_group) NOT IN " +
                "(SELECT job_name, job_group FROM qrtz_job_details)");
            System.out.println("删除孤立触发器: " + deletedTriggers + " 条");

            // 删除没有对应触发器的简单触发器记录
            int deletedSimpleTriggers = jdbcTemplate.update(
                "DELETE FROM qrtz_simple_triggers WHERE (trigger_name, trigger_group) NOT IN " +
                "(SELECT trigger_name, trigger_group FROM qrtz_triggers)");
            System.out.println("删除孤立简单触发器: " + deletedSimpleTriggers + " 条");

            // 删除没有对应触发器的Cron触发器记录
            int deletedCronTriggers = jdbcTemplate.update(
                "DELETE FROM qrtz_cron_triggers WHERE (trigger_name, trigger_group) NOT IN " +
                "(SELECT trigger_name, trigger_group FROM qrtz_triggers)");
            System.out.println("删除孤立Cron触发器: " + deletedCronTriggers + " 条");

        } catch (Exception e) {
            System.out.println("清理孤立记录失败: " + e.getMessage());
        }
    }
}
