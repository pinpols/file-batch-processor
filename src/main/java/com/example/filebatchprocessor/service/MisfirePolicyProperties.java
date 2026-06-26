package com.example.filebatchprocessor.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Misfire 策略配置属性
 */
@Data
@ConfigurationProperties(prefix = "orchestration.scheduler.misfire")
@Service
public class MisfirePolicyProperties {

    /**
     * 是否启用 misfire 恢复
     */
    private boolean enabled = true;

    /**
     * Misfire 检测窗口时间（毫秒）
     * 超过这个时间未执行的任务被认为是 misfire
     */
    private long detectionWindowMs = 300000; // 5分钟

    /**
     * Misfire 恢复延迟时间（毫秒）
     * 检测到 misfire 后，延迟多长时间进行恢复
     */
    private long recoveryDelayMs = 30000; // 30秒

    /**
     * 最大恢复尝试次数
     */
    private int maxRecoveryAttempts = 3;

    /**
     * Misfire 检查间隔时间（毫秒）
     */
    private long checkIntervalMs = 60000; // 1分钟
}
