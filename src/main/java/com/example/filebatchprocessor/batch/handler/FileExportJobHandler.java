package com.example.filebatchprocessor.batch.handler;

import com.example.filebatchprocessor.service.EnhancedExportService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 数据导出任务 Handler：定期导出数据到文件
 * 支持 CSV、JSON、Excel 等格式
 */
@Slf4j
@Component
public class FileExportJobHandler {

    private final EnhancedExportService enhancedExportService;

    public FileExportJobHandler(EnhancedExportService enhancedExportService) {
        this.enhancedExportService = enhancedExportService;
    }

    /**
     * 数据导出任务：导出指定日期数据为文件
     * 参数格式: "batchDate=2025-01-01,format=csv,outputDir=/export"
     */
    @XxlJob("fileExportJob")
    public void fileExportJob() {
        String jobParam = XxlJobHelper.getJobParam();
        
        try {
            String batchDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String format = "csv";  // 默认 CSV
            String outputDir = "export";

            if (jobParam != null && !jobParam.isEmpty()) {
                String[] params = jobParam.split(",");
                for (String param : params) {
                    if (param.startsWith("batchDate=")) {
                        batchDate = param.substring(10);
                    } else if (param.startsWith("format=")) {
                        format = param.substring(7);
                    } else if (param.startsWith("outputDir=")) {
                        outputDir = param.substring(10);
                    }
                }
            }

            log.info("Starting file export job: batchDate={}, format={}, outputDir={}", batchDate, format, outputDir);

            String outputFileName = "data_export_" + batchDate.replace("-", "") + "." + format;

            // 调用导出服务
            // 注：实际使用中需要从数据库查询数据，这里简化为示例
            if ("csv".equalsIgnoreCase(format)) {
                String[] headers = {"id", "business_key", "name", "description", "batch_date"};
                String[][] data = {
                    {"1", "key1", "name1", "desc1", batchDate},
                    {"2", "key2", "name2", "desc2", batchDate}
                };
                enhancedExportService.exportToCSV(outputDir, outputFileName, data, headers);
            } else if ("json".equalsIgnoreCase(format)) {
                // TODO: 实现 JSON 导出
                log.warn("JSON export not yet implemented");
            } else if ("excel".equalsIgnoreCase(format)) {
                // TODO: 实现 Excel 导出
                log.warn("Excel export not yet implemented");
            } else {
                throw new IllegalArgumentException("Unsupported format: " + format);
            }

            log.info("File export job completed: outputFile={}", outputFileName);
            XxlJobHelper.handleSuccess("File export completed: " + outputFileName);
        } catch (Exception e) {
            log.error("File export job failed", e);
            XxlJobHelper.handleFail("File export failed: " + e.getMessage());
        }
    }
}
