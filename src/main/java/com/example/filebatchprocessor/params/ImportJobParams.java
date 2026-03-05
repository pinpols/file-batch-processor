package com.example.filebatchprocessor.params;

import org.springframework.batch.core.job.parameters.JobParameters;

import java.util.Map;

public class ImportJobParams {

    public static final String KEY_INPUT_FILE_NAME = "input.file.name";
    public static final String KEY_BATCH_DATE = "batchDate";
    public static final String KEY_BATCH_DATE_ALT = "batch.date";
    public static final String KEY_SHARD_INDEX = "shard.index";
    public static final String KEY_SHARD_TOTAL = "shard.total";
    public static final String KEY_FILE_FORMAT = "file.format";
    public static final String KEY_FILE_DELIMITER = "file.delimiter";

    private final String inputFileName;
    private final String batchDate;
    private final Integer shardIndex;
    private final Integer shardTotal;
    private final String fileFormat;
    private final String fileDelimiter;

    private ImportJobParams(String inputFileName,
                           String batchDate,
                           Integer shardIndex,
                           Integer shardTotal,
                           String fileFormat,
                           String fileDelimiter) {
        this.inputFileName = inputFileName;
        this.batchDate = batchDate;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        this.fileFormat = fileFormat;
        this.fileDelimiter = fileDelimiter;
    }

    public static ImportJobParams from(JobParameters jobParameters) {
        JobParameterAccessor acc = new JobParameterAccessor(jobParameters);
        return from(acc);
    }

    public static ImportJobParams from(Map<String, Object> jobParameters) {
        JobParameterAccessor acc = new JobParameterAccessor(jobParameters);
        return from(acc);
    }

    private static ImportJobParams from(JobParameterAccessor acc) {
        String input = acc.getString(KEY_INPUT_FILE_NAME);
        String batchDate = acc.getString(KEY_BATCH_DATE);
        if (batchDate == null || batchDate.isBlank()) {
            batchDate = acc.getString(KEY_BATCH_DATE_ALT);
        }
        Integer shardIndex = acc.getInt(KEY_SHARD_INDEX);
        Integer shardTotal = acc.getInt(KEY_SHARD_TOTAL);
        String format = acc.getStringOrDefault(KEY_FILE_FORMAT, "CSV");
        String delimiter = acc.getStringOrDefault(KEY_FILE_DELIMITER, ",");
        return new ImportJobParams(input, batchDate, shardIndex, shardTotal, format, delimiter);
    }

    public void validateForReader() {
        if (inputFileName == null || inputFileName.isBlank()) {
            throw new IllegalArgumentException(KEY_INPUT_FILE_NAME + " is required");
        }
    }

    public void validateForWriter() {
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

    public Integer getShardIndex() {
        return shardIndex;
    }

    public Integer getShardTotal() {
        return shardTotal;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public String getFileDelimiter() {
        return fileDelimiter;
    }
}
