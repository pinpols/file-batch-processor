package com.example.filebatchprocessor.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "orchestration.scheduler")
public class SchedulerConcurrencyProperties {

    private Map<String, Integer> maxConcurrentByKey = new HashMap<>();
}
