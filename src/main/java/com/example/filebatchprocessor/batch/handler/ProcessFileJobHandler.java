package com.example.filebatchprocessor.batch.handler;


import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.JobLogRecord;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * XXL 调度入口：触发文件导入作业 processFileJob。
 */
@Component
public class ProcessFileJobHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProcessFileJobHandler.class);

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    private com.example.filebatchprocessor.repository.DlqRecordRepository dlqRecordRepository;

    @Autowired
    private com.example.filebatchprocessor.repository.JobLogRecordRepository jobLogRecordRepository;

    @Autowired
    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor batchTaskExecutor;

    @Autowired
    @Qualifier("processFileJob")
    private Job processFileJob;

    @Value("${batch.input.file:input/data.csv}")
    private String inputFile;

    @Value("${batch.dedup.window.seconds:60}")
    private long dedupWindowSeconds;

    // 短窗口去重，避免相同分片/参数在短时间内重复执行
    private final ConcurrentMap<String, Instant> recentExecutions = new ConcurrentHashMap<>();

    @XxlJob("processFileJobHandler")
    public void processFileJobHandler() {
        String param = XxlJobHelper.getJobParam();
        logger.info("XXL-JOB, processFileJobHandler start with params: {}", param);
        Instant startTime = Instant.now();

        try {
            int shardIndex = XxlJobHelper.getShardIndex();
            int shardTotal = XxlJobHelper.getShardTotal();

            Map<String, String> params = parseParams(param);
            String inputParam = params.getOrDefault("input", inputFile);
            String batchDate = params.getOrDefault("batchDate", "");
            String runMode = params.getOrDefault("runMode", "normal");
            String rerunId = params.getOrDefault("rerunId", "");
            String dedupKey = params.getOrDefault("dedupKey",
                    String.join(":", inputParam, batchDate, rerunId, shardIndex + "/" + shardTotal));
            int priority = Integer.parseInt(params.getOrDefault("priority", "0"));
            int maxRetries = Integer.parseInt(params.getOrDefault("maxRetries", "0"));
            long backoffMs = Long.parseLong(params.getOrDefault("backoffMs", "1000"));
            long maxDurationMs = Long.parseLong(params.getOrDefault("maxDurationMs", "0"));
            long timeoutMs = Long.parseLong(params.getOrDefault("timeoutMs", "0"));
            String fileFormat = params.getOrDefault("file.format", "CSV");
            String fileDelimiter = params.getOrDefault("file.delimiter", ",");

            if (isDuplicate(dedupKey)) {
                XxlJobHelper.handleSuccess("Duplicate execution skipped within dedup window");
                logger.info("Skipped duplicate execution for key {}", dedupKey);
                return;
            }

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("input.file.name", inputParam)
                    .addString("batch.date", batchDate)
                    .addString("run.mode", runMode)
                    .addString("rerun.id", rerunId)
                    .addString("file.format", fileFormat)
                    .addString("file.delimiter", fileDelimiter)
                    .addLong("shard.index", (long) shardIndex)
                    .addLong("shard.total", (long) shardTotal)
                    .addString("xxl.job.param", param)
                    .addLong("priority", (long) priority)
                    .toJobParameters();

            BatchStatus status = executeWithRetry(jobParameters, maxRetries, backoffMs, maxDurationMs, timeoutMs);

            if (status == BatchStatus.COMPLETED) {
                String msg = "Job completed successfully";
                XxlJobHelper.handleSuccess(msg);
                logger.info("Job completed successfully with status: {}", status);
                persistJobLog("processFileJob", param, status.name(), msg, startTime, Instant.now());
            } else {
                String msg = "Job failed with status: " + status;
                XxlJobHelper.handleFail(msg);
                logger.error(msg);
                persistDlq(inputParam, param, msg);
                persistJobLog("processFileJob", param, status != null ? status.name() : "UNKNOWN", msg, startTime, Instant.now());
            }

        } catch (Exception e) {
            String errorMsg = "Unexpected error: " + e.getMessage();
            logger.error(errorMsg, e);
            XxlJobHelper.handleFail(errorMsg);
            persistDlq(inputFile, param, errorMsg);
            persistJobLog("processFileJob", param, "FAILED", errorMsg, startTime, Instant.now());
        }
    }

    private Map<String, String> parseParams(String param) {
        Map<String, String> params = new HashMap<>();
        if (param != null && !param.trim().isEmpty()) {
            String[] pairs = param.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }

    private boolean isDuplicate(String dedupKey) {
        Instant now = Instant.now();
        Instant last = recentExecutions.get(dedupKey);
        if (last != null && Duration.between(last, now).getSeconds() < dedupWindowSeconds) {
            return true;
        }
        recentExecutions.put(dedupKey, now);
        return false;
    }

    private BatchStatus executeWithRetry(JobParameters params, int maxRetries, long backoffMs,
                                         long maxDurationMs, long timeoutMs) {
        int attempt = 0;
        Instant start = Instant.now();
        while (true) {
            attempt++;
            try {
                BatchStatus status = runWithTimeout(params, timeoutMs);
                if (status == BatchStatus.COMPLETED) {
                    return status;
                }
            } catch (Exception ex) {
                logger.error("Attempt {} failed: {}", attempt, ex.getMessage(), ex);
            }
            if (attempt > maxRetries) {
                return BatchStatus.FAILED;
            }
            if (maxDurationMs > 0 && Duration.between(start, Instant.now()).toMillis() > maxDurationMs) {
                logger.warn("Max duration exceeded, aborting retries");
                return BatchStatus.FAILED;
            }
            try {
                Thread.sleep(backoffMs * attempt);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return BatchStatus.FAILED;
            }
        }
    }

    private BatchStatus runWithTimeout(JobParameters params, long timeoutMs) throws Exception {
        if (timeoutMs <= 0) {
            JobExecution execution = jobLauncher.run(processFileJob, params);
            return execution.getStatus();
        }
        CompletableFuture<JobExecution> future =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return jobLauncher.run(processFileJob, params);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, batchTaskExecutor);
        JobExecution execution = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        return execution.getStatus();
    }

    private void persistDlq(String inputParam, String rawParams, String error) {
        try {
            DlqRecord record = new DlqRecord();
            record.setJobName("processFileJob");
            record.setParams(rawParams != null ? rawParams : inputParam);
            record.setErrorMessage(error);
            dlqRecordRepository.save(record);
        } catch (Exception ex) {
            logger.error("Failed to persist DLQ record", ex);
        }
    }

    // 将作业执行结果记录到数据库表 job_log_records
    private void persistJobLog(String jobName, String rawParams, String status,
                               String message, Instant start, Instant end) {
        try {
            JobLogRecord logRecord = new JobLogRecord();
            logRecord.setJobName(jobName);
            logRecord.setParams(rawParams);
            logRecord.setStatus(status);
            logRecord.setMessage(message);
            logRecord.setStartTime(java.time.LocalDateTime.ofInstant(start, java.time.ZoneId.systemDefault()));
            logRecord.setEndTime(java.time.LocalDateTime.ofInstant(end, java.time.ZoneId.systemDefault()));
            jobLogRecordRepository.save(logRecord);
        } catch (Exception e) {
            logger.error("Failed to persist job log record", e);
        }
    }
}


