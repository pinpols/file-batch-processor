package com.example.filebatchprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FileBatchProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileBatchProcessorApplication.class, args);
    }
}
