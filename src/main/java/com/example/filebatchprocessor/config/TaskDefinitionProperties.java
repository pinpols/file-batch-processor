package com.example.filebatchprocessor.config;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "orchestration")
public class TaskDefinitionProperties {
    private List<OrchestrationTaskDefinition> tasks = new ArrayList<>();
}

