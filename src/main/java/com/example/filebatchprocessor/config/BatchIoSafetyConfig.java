package com.example.filebatchprocessor.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class BatchIoSafetyConfig {

    private final Environment environment;
    private final String inputBaseDir;
    private final String outputBaseDir;

    public BatchIoSafetyConfig(
            Environment environment,
            @Value("${batch.io.input-base-dir:}") String inputBaseDir,
            @Value("${batch.io.output-base-dir:}") String outputBaseDir) {
        this.environment = environment;
        this.inputBaseDir = inputBaseDir;
        this.outputBaseDir = outputBaseDir;
    }

    @PostConstruct
    public void validateProductionBaseDirs() {
        if (!BatchProfiles.isProductionLike(environment)) {
            return;
        }
        requireConfigured("batch.io.input-base-dir", inputBaseDir);
        requireConfigured("batch.io.output-base-dir", outputBaseDir);
    }

    private void requireConfigured(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " must be configured in production-like profiles");
        }
    }
}
