package com.example.filebatchprocessor.scheduler;

import com.example.filebatchprocessor.batch.scheduler.TriggerType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
