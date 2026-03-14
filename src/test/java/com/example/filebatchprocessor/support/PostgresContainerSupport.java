package com.example.filebatchprocessor.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresContainerSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.batch.job.enabled", () -> "false");
        registry.add("batch.alert.enabled", () -> "false");
    }
}
