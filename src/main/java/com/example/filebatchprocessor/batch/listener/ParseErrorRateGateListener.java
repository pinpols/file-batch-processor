package com.example.filebatchprocessor.batch.listener;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.config.ImportParseErrorGateProperties;
import com.example.filebatchprocessor.model.QualityGateResult;
import com.example.filebatchprocessor.repository.QualityGateResultRepository;

@Component
public class ParseErrorRateGateListener implements StepExecutionListener {

    private final BatchMetrics batchMetrics;
    private final ImportParseErrorGateProperties properties;
    private final QualityGateResultRepository qualityGateResultRepository;

    public ParseErrorRateGateListener(BatchMetrics batchMetrics,
                                      ImportParseErrorGateProperties properties,
                                      QualityGateResultRepository qualityGateResultRepository) {
        this.batchMetrics = batchMetrics;
        this.properties = properties;
        this.qualityGateResultRepository = qualityGateResultRepository;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String jobName = stepExecution.getJobExecution() == null
                || stepExecution.getJobExecution().getJobInstance() == null
                ? null
                : stepExecution.getJobExecution().getJobInstance().getJobName();

        ImportParseErrorGateProperties.Rule rule = jobName == null ? null : properties.getRules().get(jobName);
        double maxRate = rule != null && rule.getMaxRate() != null ? rule.getMaxRate() : properties.getMaxRate();
        long minLines = rule != null && rule.getMinLines() != null ? rule.getMinLines() : properties.getMinLines();

        long read = stepExecution.getExecutionContext().getLong("read.count", 0L);
        long parseErr = stepExecution.getExecutionContext().getLong("parse.error.count", 0L);
        long total = read + parseErr;
        if (total < Math.max(1, minLines)) {
            persistResult(stepExecution, jobName, read, parseErr, total, total == 0 ? 0.0 : ((double) parseErr) / ((double) total), maxRate, minLines,
                    "SKIP", "total below minLines");
            return stepExecution.getExitStatus();
        }
        double rate = total == 0 ? 0.0 : ((double) parseErr) / ((double) total);
        if (rate > Math.max(0.0, maxRate)) {
            batchMetrics.counter("import_parse_error_gate_failed_total");
            stepExecution.setTerminateOnly();
            persistResult(stepExecution, jobName, read, parseErr, total, rate, maxRate, minLines,
                    "FAIL", "parse error rate too high");
            return ExitStatus.FAILED.and(new ExitStatus("PARSE_ERROR_RATE_TOO_HIGH"));
        }
        persistResult(stepExecution, jobName, read, parseErr, total, rate, maxRate, minLines,
                "PASS", null);
        return stepExecution.getExitStatus();
    }

    private void persistResult(StepExecution stepExecution,
                               String jobName,
                               long read,
                               long parseErr,
                               long total,
                               double rate,
                               double maxRate,
                               long minLines,
                               String status,
                               String message) {
        try {
            QualityGateResult result = new QualityGateResult();
            result.setGateType("IMPORT_PARSE_ERROR_RATE");
            result.setJobName(jobName);
            result.setStepName(stepExecution.getStepName());
            result.setBatchDate(stepExecution.getJobParameters() == null ? null : stepExecution.getJobParameters().getString("batchDate"));
            result.setJobExecutionId(stepExecution.getJobExecutionId());
            result.setStepExecutionId(stepExecution.getId());
            result.setReadCount(read);
            result.setParseErrorCount(parseErr);
            result.setTotalCount(total);
            result.setErrorRate(rate);
            result.setMaxRate(maxRate);
            result.setMinLines(minLines);
            result.setStatus(status);
            result.setMessage(message);
            qualityGateResultRepository.save(result);
        } catch (Exception ignored) {
        }
    }
}
