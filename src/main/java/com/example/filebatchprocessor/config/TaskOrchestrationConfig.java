package com.example.filebatchprocessor.config;


import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(TaskDefinitionProperties.class)
public class TaskOrchestrationConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("orchestration-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public CommandLineRunner registerConfiguredTasks(TaskDefinitionProperties properties,
                                                     TaskSchedulerService schedulerService) {
        return _ -> properties.getTasks().forEach(schedulerService::register);
    }
}

