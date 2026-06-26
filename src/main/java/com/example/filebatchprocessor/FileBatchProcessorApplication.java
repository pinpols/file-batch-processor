package com.example.filebatchprocessor;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@EnableBatchProcessing
@EnableJdbcJobRepository(
        dataSourceRef = "dataSource",
        transactionManagerRef = "transactionManager",
        databaseType = "POSTGRES")
public class FileBatchProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileBatchProcessorApplication.class, args);
    }
}
