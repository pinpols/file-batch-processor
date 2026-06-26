package com.example.filebatchprocessor.config;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "orchestration")
public class TaskDefinitionProperties {
    private List<OrchestrationTaskDefinition> tasks = new ArrayList<>();
}
