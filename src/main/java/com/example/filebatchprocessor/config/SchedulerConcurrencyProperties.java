package com.example.filebatchprocessor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "orchestration.scheduler")
public class SchedulerConcurrencyProperties {

    private Map<String, Integer> maxConcurrentByKey = new HashMap<>();
}
