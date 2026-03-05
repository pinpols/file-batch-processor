package com.example.filebatchprocessor.batch.processor;

import com.example.filebatchprocessor.exception.RecordValidationException;
import com.example.filebatchprocessor.model.ExportRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 导出记录处理器：处理数据转换、格式化、业务逻辑和验证
 */
@Component
public class ExportRecordProcessor implements ItemProcessor<ExportRecord, ExportRecord> {

    private static final Logger log = LoggerFactory.getLogger(ExportRecordProcessor.class);
    private static final DateTimeFormatter EXPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter EXPORT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 处理统计
    private long totalProcessed = 0;
    private long totalTransformed = 0;
    private long totalFiltered = 0;
    private long totalErrors = 0;

    @Override
    public ExportRecord process(final ExportRecord record) {
        if (record == null) {
            return null;
        }

        totalProcessed++;

        try {
            // 1. 数据验证
            validateExportRecord(record);

            // 2. 业务逻辑过滤
            if (shouldFilterRecord(record)) {
                totalFiltered++;
                log.debug("Filtered record: {}", record.getBusinessKey());
                return null;
            }

            // 3. 数据转换和格式化
            ExportRecord processedRecord = transformAndFormatRecord(record);

            totalTransformed++;
            log.debug("Processed export record: {} -> {}", record.getBusinessKey(), processedRecord.getBusinessKey());

            return processedRecord;

        } catch (Exception e) {
            totalErrors++;
            log.error("Error processing export record: {}", record.getBusinessKey(), e);
            throw new RecordValidationException("Failed to process export record: " + e.getMessage());
        }
    }

    /**
     * 验证导出记录
     */
    private void validateExportRecord(ExportRecord record) {
        // 业务键验证
        if (!StringUtils.hasText(record.getBusinessKey())) {
            throw new RecordValidationException("Export record business key is required");
        }

        // ID验证
        if (record.getId() == null || record.getId() <= 0) {
            throw new RecordValidationException("Export record ID must be positive");
        }

        // 批次日期验证
        if (!StringUtils.hasText(record.getBatchDate())) {
            throw new RecordValidationException("Export record batch date is required");
        }

        // 名称验证
        if (!StringUtils.hasText(record.getName())) {
            throw new RecordValidationException("Export record name is required");
        }
    }

    /**
     * 判断是否应该过滤记录
     */
    private boolean shouldFilterRecord(ExportRecord record) {
        // 过滤已删除的记录（如果有状态字段）
        if (hasStatusField(record) && isDeletedRecord(record)) {
            return true;
        }

        // 过滤空业务键记录
        if (!StringUtils.hasText(record.getBusinessKey())) {
            return true;
        }

        // 过滤测试数据（根据业务规则）
        if (isTestData(record)) {
            return true;
        }

        return false;
    }

    /**
     * 转换和格式化记录
     */
    private ExportRecord transformAndFormatRecord(ExportRecord record) {
        ExportRecord processedRecord = new ExportRecord();
        
        // 复制基础字段
        processedRecord.setId(record.getId());
        processedRecord.setBusinessKey(record.getBusinessKey());

        // 格式化名称字段
        String formattedName = formatNameForExport(record.getName());
        processedRecord.setName(formattedName);

        // 格式化描述字段
        String formattedDescription = formatDescriptionForExport(record.getDescription());
        processedRecord.setDescription(formattedDescription);

        // 格式化批次日期
        String formattedBatchDate = formatBatchDateForExport(record.getBatchDate());
        processedRecord.setBatchDate(formattedBatchDate);

        // 添加导出时间戳（如果有对应字段）
        addExportTimestamp(processedRecord);

        return processedRecord;
    }

    /**
     * 格式化名称字段
     */
    private String formatNameForExport(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }

        String formatted = name.trim();
        
        // 移除特殊字符
        formatted = formatted.replaceAll("[\n\r\t]", " ");
        
        // 合并多个空格
        formatted = formatted.replaceAll("\\s+", " ");
        
        // 限制长度
        if (formatted.length() > 100) {
            formatted = formatted.substring(0, 97) + "...";
        }
        
        return formatted;
    }

    /**
     * 格式化描述字段
     */
    private String formatDescriptionForExport(String description) {
        if (!StringUtils.hasText(description)) {
            return "";
        }

        String formatted = description.trim();
        
        // 移除控制字符
        formatted = formatted.replaceAll("[\\p{Cntrl}]", " ");
        
        // 合并多个空格
        formatted = formatted.replaceAll("\\s+", " ");
        
        // 限制长度
        if (formatted.length() > 500) {
            formatted = formatted.substring(0, 497) + "...";
        }
        
        return formatted;
    }

    /**
     * 格式化批次日期
     */
    private String formatBatchDateForExport(String batchDate) {
        if (!StringUtils.hasText(batchDate)) {
            return "";
        }

        try {
            // 尝试解析为日期并重新格式化
            LocalDate date = LocalDate.parse(batchDate);
            return date.format(EXPORT_DATE_FORMATTER);
        } catch (Exception e) {
            // 如果解析失败，返回原始值
            return batchDate;
        }
    }

    /**
     * 添加导出时间戳
     */
    private void addExportTimestamp(ExportRecord record) {
        // 如果ExportRecord有导出时间戳字段，可以在这里设置
        // record.setExportTimestamp(LocalDateTime.now().format(EXPORT_DATETIME_FORMATTER));
    }

    /**
     * 检查记录是否有状态字段
     */
    private boolean hasStatusField(ExportRecord record) {
        // 通过反射或其他方式检查是否有状态字段
        // 这里简化处理，假设有状态字段
        return false;
    }

    /**
     * 检查是否为已删除记录
     */
    private boolean isDeletedRecord(ExportRecord record) {
        // 检查记录状态是否为已删除
        return false;
    }

    /**
     * 检查是否为测试数据
     */
    private boolean isTestData(ExportRecord record) {
        // 检查是否为测试数据（根据业务规则）
        return record.getBusinessKey() != null && record.getBusinessKey().startsWith("TEST_");
    }

    /**
     * 格式化数字字段（如果有）
     */
    private String formatNumberForExport(BigDecimal number) {
        if (number == null) {
            return "0";
        }
        
        return number.setScale(2, RoundingMode.HALF_UP).toString();
    }

    /**
     * 获取处理统计信息
     */
    public ProcessorStats getStats() {
        return new ProcessorStats(totalProcessed, totalTransformed, totalFiltered, totalErrors);
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalProcessed = 0;
        totalTransformed = 0;
        totalFiltered = 0;
        totalErrors = 0;
    }

    /**
     * 处理器统计信息
     */
    public static class ProcessorStats {
        private final long totalProcessed;
        private final long totalTransformed;
        private final long totalFiltered;
        private final long totalErrors;

        public ProcessorStats(long totalProcessed, long totalTransformed, long totalFiltered, long totalErrors) {
            this.totalProcessed = totalProcessed;
            this.totalTransformed = totalTransformed;
            this.totalFiltered = totalFiltered;
            this.totalErrors = totalErrors;
        }

        public long getTotalProcessed() {
            return totalProcessed;
        }

        public long getTotalTransformed() {
            return totalTransformed;
        }

        public long getTotalFiltered() {
            return totalFiltered;
        }

        public long getTotalErrors() {
            return totalErrors;
        }

        public double getSuccessRate() {
            return totalProcessed > 0 ? (double) totalTransformed / totalProcessed : 0.0;
        }

        @Override
        public String toString() {
            return String.format("ProcessorStats{processed=%d, transformed=%d, filtered=%d, errors=%d, successRate=%.2f%%}", 
                    totalProcessed, totalTransformed, totalFiltered, totalErrors, getSuccessRate() * 100);
        }
    }
}
