package com.example.filebatchprocessor.batch.handler;

import com.example.filebatchprocessor.service.PartitionedImportService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 分区导入任务 Handler：定期从数据源导入数据到分区表
 */
@Slf4j
@Component
public class PartitionedImportJobHandler {

    private final PartitionedImportService partitionedImportService;

    public PartitionedImportJobHandler(PartitionedImportService partitionedImportService) {
        this.partitionedImportService = partitionedImportService;
    }

    /**
     * 分区导入任务：按批次导入记录到分区表
     * 可配置批期日期、分区键等参数
     */
    @XxlJob("partitionedImportJob")
    public void partitionedImportJob() {
        String batchDate = XxlJobHelper.getJobParam();
        if (batchDate == null || batchDate.isEmpty()) {
            batchDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        
        try {
            log.info("Starting partitioned import job for batchDate={}", batchDate);
            
            // 示例：导入样本数据
            String partitionKey = LocalDate.parse(batchDate).format(DateTimeFormatter.ofPattern("yyyy_MM"));
            log.info("Partition key: {}", partitionKey);
            
            // 实际使用中，这里会从消息队列、文件、或其他数据源读取记录进行导入
            // 示例代码：
            // partitionedImportService.importRecord("key1", "name1", "desc1", batchDate, null, null);
            // partitionedImportService.importRecord("key2", "name2", "desc2", batchDate, null, null);
            
            log.info("Partitioned import job completed for batchDate={}", batchDate);
            XxlJobHelper.handleSuccess("Partitioned import completed");
        } catch (Exception e) {
            log.error("Partitioned import job failed for batchDate={}", batchDate, e);
            XxlJobHelper.handleFail("Partitioned import failed: " + e.getMessage());
        }
    }
}
