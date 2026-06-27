package com.example.filebatchprocessor.params;

import java.util.Map;
import org.springframework.batch.core.job.parameters.JobParameters;

public class ImportJobParams {

    public static final String KEY_INPUT_FILE_NAME = "input.file.name";
    public static final String KEY_BATCH_DATE = "batchDate";
    public static final String KEY_BATCH_DATE_ALT = "batch.date";
    public static final String KEY_SHARD_INDEX = "shard.index";
    public static final String KEY_SHARD_TOTAL = "shard.total";
    public static final String KEY_FILE_FORMAT = "file.format";
    public static final String KEY_FILE_DELIMITER = "file.delimiter";
    public static final String KEY_EXCEL_SHEET_INDEX = "excel.sheet.index";
    public static final String KEY_EXCEL_SHEET_NAME = "excel.sheet.name";

    private final String inputFileName;
    private final String batchDate;
    private final Integer shardIndex;
    private final Integer shardTotal;
    private final String fileFormat;
    private final String fileDelimiter;
    private final int excelSheetIndex;
    private final String excelSheetName;

    private ImportJobParams(
            String inputFileName,
            String batchDate,
            Integer shardIndex,
            Integer shardTotal,
            String fileFormat,
            String fileDelimiter,
            int excelSheetIndex,
            String excelSheetName) {
        this.inputFileName = inputFileName;
        this.batchDate = batchDate;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        this.fileFormat = fileFormat;
        this.fileDelimiter = fileDelimiter;
        this.excelSheetIndex = excelSheetIndex;
        this.excelSheetName = excelSheetName;
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
        int excelSheetIndex = acc.getIntOrDefault(KEY_EXCEL_SHEET_INDEX, 0);
        String excelSheetName = acc.getString(KEY_EXCEL_SHEET_NAME);
        return new ImportJobParams(
                input, batchDate, shardIndex, shardTotal, format, delimiter, excelSheetIndex, excelSheetName);
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

    public int getExcelSheetIndex() {
        return excelSheetIndex;
    }

    public String getExcelSheetName() {
        return excelSheetName;
    }
}
