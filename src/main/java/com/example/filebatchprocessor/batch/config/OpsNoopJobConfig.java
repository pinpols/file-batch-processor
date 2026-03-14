package com.example.filebatchprocessor.batch.config;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class OpsNoopJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public OpsNoopJobConfig(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    @StepScope
    public Tasklet opsNoopTasklet() {
        return (contribution, chunkContext) -> RepeatStatus.FINISHED;
    }

    @Bean
    public Step opsNoopStep(@Qualifier("opsNoopTasklet") Tasklet opsNoopTasklet) {
        return new StepBuilder("opsNoopStep", jobRepository)
                .tasklet(opsNoopTasklet, transactionManager)
                .build();
    }

    // Compatibility aliases for task_definition jobName values that are not wired yet.
    @Bean(name = {
            "opsNoopJob",
            "batchRestartJob"
    })
    public Job opsNoopJob(@Qualifier("opsNoopStep") Step opsNoopStep) {
        return new JobBuilder("opsNoopJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(opsNoopStep)
                .build();
    }
}
