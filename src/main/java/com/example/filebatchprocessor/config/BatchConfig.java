package com.example.filebatchprocessor.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Autowired
    private JobRepository jobRepository;
    private JobRepository jobRepository;

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
     * JobOperator 替代已弃用的 JobLauncher
     * 提供更强大的作业管理功能
     */
    @Bean(name = "asyncJobLauncher")
    public JobOperator asyncJobOperator() {
        SimpleJobOperator jobOperator = new SimpleJobOperator();
        jobOperator.setJobRepository(jobRepository);
        jobOperator.setJobRegistry(new MapJobRegistry());
        jobOperator.setTaskExecutor(batchTaskExecutor());
        return jobOperator;
    }

//
//    @Bean
//    public Job processFileJob() {
//         return new JobBuilder("importJob", jobRepository)
//                .incrementer(new RunIdIncrementer())
//                .listener(listener)
//                .start(importStep)
//                .build();
//    }

}
