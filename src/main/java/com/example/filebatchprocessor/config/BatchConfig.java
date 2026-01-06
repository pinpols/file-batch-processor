package com.example.filebatchprocessor.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Spring Batch 配置
 * 
 * 注意：JobLauncher 在 Spring Batch 6.0 中已弃用，建议迁移到 JobOperator
 * 当前使用 JobLauncher 作为过渡方案，未来版本需要迁移
 */
@Configuration
@EnableBatchProcessing
@SuppressWarnings("deprecation")
public class BatchConfig {

    @Bean
    public ThreadPoolTaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("batch-executor-");
        executor.initialize();
        return executor;
    }

    /**
     * 异步 JobLauncher 实现
     * 
     * @deprecated JobLauncher 在 Spring Batch 6.0 中已弃用
     * 建议迁移到 JobOperator，但需要重构调用代码
     * 当前保留以保持向后兼容性
     * 
     * 注意：SimpleJobLauncher 在 Spring Batch 6.0 中已被移除
     * 这里使用 @EnableBatchProcessing 自动配置的 JobLauncher
     * 如果需要自定义名称，可以通过 @Primary 或 @Qualifier 来区分
     */
    @Bean(name = "asyncJobLauncher")
    @Deprecated
    public JobLauncher asyncJobLauncher(JobLauncher jobLauncher) {
        // @EnableBatchProcessing 会自动创建一个 JobLauncher bean
        // 我们直接使用它，如果需要异步可以通过配置 TaskExecutor 实现
        return jobLauncher;
    }


}
