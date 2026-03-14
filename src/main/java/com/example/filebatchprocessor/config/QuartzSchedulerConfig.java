package com.example.filebatchprocessor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class QuartzSchedulerConfig {

    @Bean
    public SchedulerFactoryBeanCustomizer quartzSchedulerFactoryBeanCustomizer(
            PlatformTransactionManager transactionManager) {
        return schedulerFactoryBean -> schedulerFactoryBean.setTransactionManager(transactionManager);
    }
}
