package com.example.filebatchprocessor.batch.listener;

import com.example.filebatchprocessor.observability.MdcContext;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Step 级别的 MDC 上下文监听器，用于填充分片相关字段。
 */
@Slf4j
@Component
public class ShardContextListener {

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        Map<String, String> mdcCtx = new HashMap<>();

        // 基础字段（与 TaskSchedulerService 保持一致）
        mdcCtx.put("job_name", stepExecution.getJobExecution().getJobInstance().getJobName());
        mdcCtx.put("step_name", stepExecution.getStepName());

        // 批次与执行 ID
        String batchDate = stepExecution.getJobParameters() != null
                ? stepExecution.getJobParameters().getString("batchDate")
                : null;
        if (batchDate != null) {
            mdcCtx.put("batch_date", batchDate);
        }
        String executionId = stepExecution.getJobParameters() != null
                ? stepExecution.getJobParameters().getString("executionId")
                : null;
        if (executionId != null) {
            mdcCtx.put("execution_id", executionId);
        }

        // 分片字段（任务分片场景）
        String shardIndex = stepExecution.getJobParameters() != null
                ? stepExecution.getJobParameters().getString("shardIndex")
                : null;
        if (shardIndex != null) {
            mdcCtx.put("shard_index", shardIndex);
        }
        String shardTotal = stepExecution.getJobParameters() != null
                ? stepExecution.getJobParameters().getString("shardTotal")
                : null;
        if (shardTotal != null) {
            mdcCtx.put("shard_total", shardTotal);
        }

        // 业务字段（可扩展）
        String targetSystem = stepExecution.getJobParameters() != null
                ? stepExecution.getJobParameters().getString("targetSystem")
                : null;
        if (targetSystem != null) {
            mdcCtx.put("target_system", targetSystem);
        }

        MdcContext.withContext(mdcCtx, () -> {
            log.info(
                    "Step started with MDC context: stepName={}, shardIndex={}",
                    stepExecution.getStepName(),
                    shardIndex);
        });
    }
}
