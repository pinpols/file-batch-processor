package com.example.filebatchprocessor.config;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StreamUtils;

/**
 * Spring Batch 配置
 *
 * 注意：JobLauncher 在 Spring Batch 6.0 中已弃用，建议迁移到 JobOperator
 * 当前使用 JobLauncher 作为过渡方案，未来版本需要迁移
 */
@Configuration
@SuppressWarnings("deprecation")
public class BatchConfig {

    @Value("${batch.executor.core-pool-size:10}")
    private int corePoolSize;

    @Value("${batch.executor.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${batch.executor.queue-capacity:200}")
    private int queueCapacity;

    @Bean
    public ThreadPoolTaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(corePoolSize, maxPoolSize));
        executor.setQueueCapacity(Math.max(10, queueCapacity));
        executor.setThreadNamePrefix("batch-executor-");
        // #23 优雅停机:停机时等在途任务跑完(上限 30s),避免 SIGTERM 直接丢弃在途批处理
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    /**
     * #27 澄清:此 launcher 实为**同步**(直接复用框架默认 JobLauncher)。这是契约要求而非缺陷——
     * DagOrchestratorService / DlqCompensationService 等调用方在 run() 返回后立即读取 BatchStatus
     * 与 JobExecution 结果,必须等作业跑完;改成异步会让它们读到 STARTING 而出错。名称沿用是为兼容
     * 既有 @Qualifier("asyncJobLauncher") 注入点;切勿在此包装 TaskExecutor 改成异步。
     */
    @Bean(name = "asyncJobLauncher")
    public JobLauncher asyncJobLauncher(JobLauncher jobLauncher) {
        return jobLauncher;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(
            name = "batch.schema.startup-bootstrap.enabled",
            havingValue = "true",
            matchIfMissing = false)
    public ApplicationRunner springBatchMetadataSchemaInitializer(DataSource dataSource) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) throws Exception {
                try (Connection connection = dataSource.getConnection();
                        Statement statement = connection.createStatement()) {
                    ClassPathResource resource =
                            new ClassPathResource("db/migration/V1_24__spring_batch_metadata_tables.sql");
                    String ddl = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                    statement.execute(ddl);
                }
            }
        };
    }
}
