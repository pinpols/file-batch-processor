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
        executor.initialize();
        return executor;
    }

    @Bean
    public JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    @Bean(name = "asyncJobLauncher")
    @Deprecated
    public JobLauncher asyncJobLauncher(JobLauncher jobLauncher) {
        // 我们直接使用它，如果需要异步可以通过配置 TaskExecutor 实现
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
