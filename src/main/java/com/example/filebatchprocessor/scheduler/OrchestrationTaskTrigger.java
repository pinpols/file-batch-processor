package com.example.filebatchprocessor.scheduler;

import com.example.filebatchprocessor.batch.scheduler.TriggerType;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationTaskTrigger {
    private TriggerType type;
    private String cron;
    private Long fixedRateMs;
    private Long fixedDelayMs;
    private Instant oneTimeAt;
}

