package com.example.filebatchprocessor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "orchestration.circuit-breaker")
public class CircuitBreakerProperties {

    private long windowSize = 10L;

    private double failureRateThreshold = 0.5;

    private long cooldownDurationMs = 300_000L; // 5 minutes
}
