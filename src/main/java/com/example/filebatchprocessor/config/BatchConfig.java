package com.example.filebatchprocessor.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;

/**
 * Spring Batch 配置
 * 
 * 注意：JobLauncher 在 Spring Batch 6.0 中已弃用，建议迁移到 JobOperator
 * 当前使用 JobLauncher 作为过渡方案，未来版本需要迁移
 */
@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository(
        dataSourceRef = "dataSource",
        transactionManagerRef = "transactionManager",
        databaseType = "POSTGRES"
)
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

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(name = "batch.schema.startup-bootstrap.enabled", havingValue = "true", matchIfMissing = false)
    public ApplicationRunner springBatchMetadataSchemaInitializer(DataSource dataSource) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) throws Exception {
                try (Connection connection = dataSource.getConnection()) {
                    ClassPathResource resource = new ClassPathResource("db/migration/V1_24__spring_batch_metadata_tables.sql");
                    String ddl = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                    connection.createStatement().execute(ddl);
                }
            }
        };
    }


}
