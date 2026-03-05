package com.example.filebatchprocessor.params;

import org.springframework.batch.core.job.parameters.JobParameters;

import java.util.Map;

public class ReconcileJobParams {

    public static final String KEY_INPUT_FILE_NAME = "input.file.name";
    public static final String KEY_BATCH_DATE = "batch.date";
    public static final String KEY_BATCH_DATE_ALT = "batchDate";
    public static final String KEY_TARGET_JOB_NAME = "job.name";

    private final String inputFileName;
    private final String batchDate;
    private final String targetJobName;

    private ReconcileJobParams(String inputFileName, String batchDate, String targetJobName) {
        this.inputFileName = inputFileName;
        this.batchDate = batchDate;
        this.targetJobName = targetJobName;
    }

    public static ReconcileJobParams from(JobParameters jobParameters) {
        JobParameterAccessor acc = new JobParameterAccessor(jobParameters);
        return from(acc);
    }

    public static ReconcileJobParams from(Map<String, Object> jobParameters) {
        JobParameterAccessor acc = new JobParameterAccessor(jobParameters);
        return from(acc);
    }

    private static ReconcileJobParams from(JobParameterAccessor acc) {
        String input = acc.getString(KEY_INPUT_FILE_NAME);
        String batch = acc.getString(KEY_BATCH_DATE);
        if (batch == null || batch.isBlank()) {
            batch = acc.getString(KEY_BATCH_DATE_ALT);
        }
        String jobName = acc.getStringOrDefault(KEY_TARGET_JOB_NAME, "importJob");
        return new ReconcileJobParams(input, batch, jobName);
    }

    public void validate() {
        if (inputFileName == null || inputFileName.isBlank()) {
            throw new IllegalArgumentException(KEY_INPUT_FILE_NAME + " is required");
        }
        if (batchDate == null || batchDate.isBlank()) {
            throw new IllegalArgumentException(KEY_BATCH_DATE + "/" + KEY_BATCH_DATE_ALT + " is required");
        }
    }

    public String getInputFileName() {
        return inputFileName;
    }

    public String getBatchDate() {
        return batchDate;
    }

    public String getTargetJobName() {
        return targetJobName;
    }
}
