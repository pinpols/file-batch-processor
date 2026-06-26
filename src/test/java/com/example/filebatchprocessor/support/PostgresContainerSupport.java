package com.example.filebatchprocessor.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresContainerSupport {

    private static final Logger log = LoggerFactory.getLogger(PostgresContainerSupport.class);

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("postgres");

    private static final Object MONITOR = new Object();

    private static volatile DatabaseConfig databaseConfig;

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        DatabaseConfig config = resolveDatabaseConfig();
        registry.add("spring.datasource.url", config::jdbcUrl);
        registry.add("spring.datasource.username", config::username);
        registry.add("spring.datasource.password", config::password);
        registry.add("spring.datasource.driver-class-name", config::driverClassName);
        registry.add("spring.flyway.default-schema", config::schema);
        registry.add("spring.flyway.schemas", config::schema);
        registry.add("spring.jpa.properties.hibernate.default_schema", config::schema);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.batch.job.enabled", () -> "false");
        registry.add("batch.alert.enabled", () -> "false");
    }

    private static DatabaseConfig resolveDatabaseConfig() {
        if (databaseConfig != null) {
            return databaseConfig;
        }
        synchronized (MONITOR) {
            if (databaseConfig == null) {
                databaseConfig = initializeDatabaseConfig();
            }
            return databaseConfig;
        }
    }

    private static DatabaseConfig initializeDatabaseConfig() {
        try {
            if (!POSTGRES.isRunning()) {
                POSTGRES.start();
            }
            DatabaseConfig config = createSchemaScopedConfig(
                    POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(),
                    POSTGRES.getPassword(),
                    POSTGRES.getDriverClassName(),
                    "testcontainers");
            log.info("Using Testcontainers PostgreSQL for integration tests: schema={}", config.schema());
            return config;
        } catch (Throwable dockerFailure) {
            log.warn("Falling back to local PostgreSQL for integration tests: {}", dockerFailure.getMessage());
            return createLocalFallbackConfig(dockerFailure);
        }
    }

    private static DatabaseConfig createLocalFallbackConfig(Throwable dockerFailure) {
        String jdbcUrl =
                readSetting("TEST_POSTGRES_URL", "test.postgres.url", "jdbc:postgresql://localhost:5432/postgres");
        String username = readSetting("TEST_POSTGRES_USERNAME", "test.postgres.username", "postgres");
        String password = readSetting("TEST_POSTGRES_PASSWORD", "test.postgres.password", "postgres");
        String driverClassName = "org.postgresql.Driver";
        try {
            DatabaseConfig config = createSchemaScopedConfig(jdbcUrl, username, password, driverClassName, "local");
            log.info("Using local PostgreSQL fallback for integration tests: schema={}", config.schema());
            return config;
        } catch (RuntimeException localFailure) {
            localFailure.addSuppressed(dockerFailure);
            throw localFailure;
        }
    }

    private static DatabaseConfig createSchemaScopedConfig(
            String baseJdbcUrl, String username, String password, String driverClassName, String prefix) {
        String schema = buildSchemaName(prefix);
        ensureSchemaExists(baseJdbcUrl, username, password, schema);
        return new DatabaseConfig(
                appendCurrentSchema(baseJdbcUrl, schema), username, password, driverClassName, schema);
    }

    private static String readSetting(String envKey, String systemPropertyKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String systemValue = System.getProperty(systemPropertyKey);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        return defaultValue;
    }

    private static String buildSchemaName(String prefix) {
        return (prefix + "_" + UUID.randomUUID().toString().replace("-", "")).toLowerCase(Locale.ROOT);
    }

    private static void ensureSchemaExists(String jdbcUrl, String username, String password, String schema) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
                Statement statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + schema);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to prepare PostgreSQL schema for integration tests: " + schema, ex);
        }
    }

    private static String appendCurrentSchema(String jdbcUrl, String schema) {
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private record DatabaseConfig(
            String jdbcUrl, String username, String password, String driverClassName, String schema) {}
}
