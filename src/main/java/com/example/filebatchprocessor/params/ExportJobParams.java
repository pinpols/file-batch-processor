package com.example.filebatchprocessor.params;

import org.springframework.batch.core.job.parameters.JobParameters;

import java.util.Map;

public class ExportJobParams {

    public static final String KEY_EXPORT_SQL = "export.sql";
    public static final String KEY_OUTPUT_FILE_NAME = "output.file.name";

    private final String exportSql;
    private final String outputFileName;

    private ExportJobParams(String exportSql, String outputFileName) {
        this.exportSql = exportSql;
        this.outputFileName = outputFileName;
    }

    public static ExportJobParams from(JobParameters jobParameters) {
        JobParameterAccessor acc = new JobParameterAccessor(jobParameters);
        return from(acc);
    }

    public static ExportJobParams from(Map<String, Object> jobParameters) {
        JobParameterAccessor acc = new JobParameterAccessor(jobParameters);
        return from(acc);
    }

    private static ExportJobParams from(JobParameterAccessor acc) {
        String sql = acc.getString(KEY_EXPORT_SQL);
        String output = acc.getString(KEY_OUTPUT_FILE_NAME);
        return new ExportJobParams(sql, output);
    }

    public String getExportSql() {
        return exportSql;
    }

    public String getOutputFileName() {
        return outputFileName;
    }
}
