package com.example.filebatchprocessor.service;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 增强型导出服务：
 * 1. 支持多种文件格式（CSV、JSON、XML等）
 * 2. 支持增量导出
 * 3. 支持文件压缩
 * 4. 支持字段映射和转换
 */
@Slf4j
@Service
public class FileExportService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final ObjectMapper objectMapper;
    private final FileAssetService fileAssetService;
    private final FileProcessLogService fileProcessLogService;

    @Autowired
    public FileExportService(
            ObjectMapper objectMapper, FileAssetService fileAssetService, FileProcessLogService fileProcessLogService) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.fileAssetService = fileAssetService;
        this.fileProcessLogService = fileProcessLogService;
    }

    public FileExportService() {
        this(new ObjectMapper(), null, null);
    }

    public String exportDemoData(String outputDir, String fileName, String batchDate, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            String[] headers = {"id", "business_key", "name", "description", "batch_date"};
            String[][] data = {
                {"1", "key1", "name1", "desc1", batchDate},
                {"2", "key2", "name2", "desc2", batchDate}
            };
            return exportToCSV(outputDir, fileName, data, headers);
        }
        if ("json".equalsIgnoreCase(format)) {
            List<Map<String, String>> data = List.of(
                    Map.of(
                            "id",
                            "1",
                            "business_key",
                            "key1",
                            "name",
                            "name1",
                            "description",
                            "desc1",
                            "batch_date",
                            batchDate),
                    Map.of(
                            "id",
                            "2",
                            "business_key",
                            "key2",
                            "name",
                            "name2",
                            "description",
                            "desc2",
                            "batch_date",
                            batchDate));
            return exportToJSON(outputDir, fileName, data);
        }
        if ("excel".equalsIgnoreCase(format)) {
            String[] headers = {"id", "business_key", "name", "description", "batch_date"};
            String[][] data = {
                {"1", "key1", "name1", "desc1", batchDate},
                {"2", "key2", "name2", "desc2", batchDate}
            };
            return exportToExcel(outputDir, fileName, data, headers);
        }
        throw new IllegalArgumentException("Unsupported format: " + format);
    }

    /**
     * 导出为 CSV 格式
     */
    public String exportToCSV(String outputDir, String fileName, String[][] data, String[] headers) {
        log.info("Exporting to CSV: {}", fileName);

        try {
            // 确保输出目录存在
            Files.createDirectories(Paths.get(outputDir));

            // 生成完整路径
            String filePath = Paths.get(outputDir, fileName).toString();

            // 写入 CSV 文件(显式 UTF-8,避免依赖平台默认编码)
            try (CSVWriter writer = new CSVWriter(new FileWriter(filePath, StandardCharsets.UTF_8))) {
                // 写入头
                if (headers != null && headers.length > 0) {
                    writer.writeNext(headers);
                }

                // 写入数据
                if (data != null) {
                    for (String[] row : data) {
                        writer.writeNext(row);
                    }
                }
            }

            log.info("CSV export completed: {}", filePath);
            registerGeneratedFile(
                    fileName,
                    filePath,
                    "FILE_EXPORT",
                    null,
                    Map.of("format", "csv", "rowCount", data == null ? 0 : data.length));
            return filePath;
        } catch (Exception e) {
            log.error("Failed to export CSV: {}", fileName, e);
            throw new RuntimeException("Failed to export CSV: " + e.getMessage(), e);
        }
    }

    /**
     * 导出为 JSON 格式（使用简单 JSON 格式）
     */
    public String exportToJSON(String outputDir, String fileName, List<? extends Object> data) {
        log.info("Exporting to JSON: {}", fileName);

        try {
            Files.createDirectories(Paths.get(outputDir));
            String filePath = Paths.get(outputDir, fileName).toString();
            objectMapper.writeValue(Paths.get(filePath).toFile(), data);

            log.info("JSON export completed: {}", filePath);
            registerGeneratedFile(
                    fileName,
                    filePath,
                    "FILE_EXPORT",
                    null,
                    Map.of("format", "json", "rowCount", data == null ? 0 : data.size()));
            return filePath;
        } catch (Exception e) {
            log.error("Failed to export JSON: {}", fileName, e);
            throw new RuntimeException("Failed to export JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 导出为 Excel 格式
     */
    public String exportToExcel(String outputDir, String fileName, String[][] data, String[] headers) {
        log.info("Exporting to Excel: {}", fileName);

        try {
            Files.createDirectories(Paths.get(outputDir));
            String filePath = Paths.get(outputDir, fileName).toString();

            try (ExcelWriter writer = ExcelUtil.getWriter(filePath)) {
                if (headers != null && headers.length > 0) {
                    writer.writeRow(Arrays.asList(headers));
                }
                if (data != null) {
                    for (String[] row : data) {
                        writer.writeRow(Arrays.asList(row));
                    }
                }
                writer.flush();
            }
            registerGeneratedFile(
                    fileName,
                    filePath,
                    "FILE_EXPORT",
                    null,
                    Map.of("format", "excel", "rowCount", data == null ? 0 : data.length));
            return filePath;
        } catch (Exception e) {
            log.error("Failed to export Excel: {}", fileName, e);
            throw new RuntimeException("Failed to export Excel: " + e.getMessage(), e);
        }
    }

    /**
     * 导出为压缩格式（ZIP）
     */
    public String exportCompressed(String outputDir, String fileName, String sourceFilePath) {
        log.info("Exporting compressed file: {}", fileName);

        try {
            Files.createDirectories(Paths.get(outputDir));
            String zipPath = Paths.get(outputDir, fileName).toString();
            File source = new File(sourceFilePath);
            if (!source.exists() || !source.isFile()) {
                throw new IllegalArgumentException("Source file not found: " + sourceFilePath);
            }

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(Paths.get(zipPath)))) {
                zos.putNextEntry(new ZipEntry(source.getName()));
                Files.copy(source.toPath(), zos);
                zos.closeEntry();
            }

            registerGeneratedFile(
                    fileName, zipPath, "FILE_EXPORT", null, Map.of("format", "zip", "sourceFile", sourceFilePath));
            return zipPath;
        } catch (Exception e) {
            log.error("Failed to export compressed file: {}", fileName, e);
            throw new RuntimeException("Failed to export compressed file: " + e.getMessage(), e);
        }
    }

    /**
     * 生成带时间戳的文件名
     */
    public String generateFileName(String prefix, String extension) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        return prefix + "_" + timestamp + "." + extension;
    }

    /**
     * 验证文件是否存在且可读
     */
    public boolean validateExportFile(String filePath) {
        try {
            File file = new File(filePath);
            return file.exists() && file.isFile() && file.canRead() && file.length() > 0;
        } catch (Exception e) {
            log.error("Failed to validate export file: {}", filePath, e);
            return false;
        }
    }

    /**
     * 获取文件信息
     */
    public ExportFileInfo getFileInfo(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        return new ExportFileInfo(file.getName(), file.getAbsolutePath(), file.length(), file.lastModified());
    }

    /**
     * 文件信息类
     */
    public static class ExportFileInfo {
        public String fileName;
        public String absolutePath;
        public long fileSize;
        public long lastModified;

        public ExportFileInfo(String fileName, String absolutePath, long fileSize, long lastModified) {
            this.fileName = fileName;
            this.absolutePath = absolutePath;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
        }
    }

    private void registerGeneratedFile(
            String fileName, String filePath, String bizType, String batchDate, Map<String, Object> metadata) {
        if (fileAssetService == null) {
            return;
        }
        var fileRecord = fileAssetService.registerOutboundFile(
                fileName, filePath, bizType, batchDate, null, null, "PROCESSED", metadata);
        if (fileProcessLogService != null) {
            fileProcessLogService.log(
                    fileRecord.getId(),
                    "exportFile",
                    "EXPORT",
                    null,
                    "PROCESSED",
                    "SUCCESS",
                    null,
                    "fileExportJob",
                    0,
                    null,
                    null,
                    metadata);
        }
    }
}
