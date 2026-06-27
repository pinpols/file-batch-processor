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
    public static final String KEY_INPUT_FILE_ENCRYPTED = "input.file.encrypted";
    public static final String KEY_INPUT_FILE_COMPRESSION = "input.file.compression";

    private final String inputFileName;
    private final String batchDate;
    private final Integer shardIndex;
    private final Integer shardTotal;
    private final String fileFormat;
    private final String fileDelimiter;
    private final int excelSheetIndex;
    private final String excelSheetName;
    private final Boolean inputFileEncrypted;
    private final String inputFileCompression;

    private ImportJobParams(
            String inputFileName,
            String batchDate,
            Integer shardIndex,
            Integer shardTotal,
            String fileFormat,
            String fileDelimiter,
            int excelSheetIndex,
            String excelSheetName,
            Boolean inputFileEncrypted,
            String inputFileCompression) {
        this.inputFileName = inputFileName;
        this.batchDate = batchDate;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        this.fileFormat = fileFormat;
        this.fileDelimiter = fileDelimiter;
        this.excelSheetIndex = excelSheetIndex;
        this.excelSheetName = excelSheetName;
        this.inputFileEncrypted = inputFileEncrypted;
        this.inputFileCompression = inputFileCompression;
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
        String encryptedRaw = acc.getString(KEY_INPUT_FILE_ENCRYPTED);
        // 缺省 null 表示"未显式指定",交给后缀判定;不要默认 false。
        Boolean inputFileEncrypted = encryptedRaw == null ? null : Boolean.valueOf(encryptedRaw);
        String inputFileCompression = acc.getString(KEY_INPUT_FILE_COMPRESSION);
        return new ImportJobParams(
                input,
                batchDate,
                shardIndex,
                shardTotal,
                format,
                delimiter,
                excelSheetIndex,
                excelSheetName,
                inputFileEncrypted,
                inputFileCompression);
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

    public Boolean getInputFileEncrypted() {
        return inputFileEncrypted;
    }

    public String getInputFileCompression() {
        return inputFileCompression;
    }
}
