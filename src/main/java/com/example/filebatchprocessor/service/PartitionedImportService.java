package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.exception.TransientImportException;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 分区表导入服务：
 * 1. 支持导入到按时间分区的表
 * 2. 提供分区管理功能
 * 3. 支持分区查询和统计
 */
@Slf4j
@Service
@Transactional
public class PartitionedImportService {

    private final ImportedRecordPartitionedRepository partitionedRepository;
    private final JdbcTemplate jdbcTemplate;
    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public PartitionedImportService(
            ImportedRecordPartitionedRepository partitionedRepository, JdbcTemplate jdbcTemplate) {
        this.partitionedRepository = partitionedRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 导入记录到分区表
     */
    public ImportedRecordPartitioned importRecord(
            String businessKey,
            String name,
            String description,
            String batchDate,
            String sourceFileName,
            String checksum) {
        String normalizedBatchDate =
                StringUtils.hasText(batchDate) ? batchDate : LocalDate.now().format(BATCH_DATE_FORMATTER);
        log.info("Importing record to partitioned table: key={}, batchDate={}", businessKey, normalizedBatchDate);

        try {
            // 生成分区键（yyyy_MM 格式）
            String partitionKey = generatePartitionKey(normalizedBatchDate);

            // 检查是否已存在
            var existing = partitionedRepository.findByBusinessKeyAndBatchDate(businessKey, normalizedBatchDate);
            if (existing.isPresent()) {
                // 幂等：重复导入时直接返回已存在记录，避免抛错污染日志
                log.debug(
                        "Record already exists (idempotent hit): key={}, batchDate={}",
                        businessKey,
                        normalizedBatchDate);
                return existing.get();
            }

            ImportedRecordPartitioned record = new ImportedRecordPartitioned();
            record.setBusinessKey(businessKey);
            record.setName(name);
            record.setDescription(description);
            record.setBatchDate(normalizedBatchDate);
            record.setPartitionKey(partitionKey);
            record.setSourceFileName(sourceFileName);
            record.setChecksum(checksum);
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());

            ImportedRecordPartitioned saved = partitionedRepository.save(record);
            log.info("Record imported successfully: id={}, partition={}", saved.getId(), partitionKey);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // 并发下可能出现先查不存在、后插入冲突，按幂等命中处理
            return partitionedRepository
                    .findByBusinessKeyAndBatchDate(businessKey, normalizedBatchDate)
                    .map(existing -> {
                        log.debug(
                                "Record already exists after concurrent insert (idempotent hit): key={}, batchDate={}",
                                businessKey,
                                normalizedBatchDate);
                        return existing;
                    })
                    .orElseThrow(() -> e);
        } catch (TransientDataAccessException e) {
            throw new TransientImportException("Transient failure while importing record", e);
        } catch (Exception e) {
            log.error("Failed to import record: key={}", businessKey, e);
            throw new RuntimeException("Failed to import record: " + e.getMessage(), e);
        }
    }

    /**
     * 批量导入记录
     */
    public List<ImportedRecordPartitioned> importRecordsBatch(List<ImportedRecordPartitioned> records) {
        log.info("Batch importing {} records to partitioned table", records.size());

        try {
            records.forEach(record -> {
                if (record.getPartitionKey() == null) {
                    record.setPartitionKey(generatePartitionKey(record.getBatchDate()));
                }
                record.setCreatedAt(LocalDateTime.now());
                record.setUpdatedAt(LocalDateTime.now());
            });

            List<ImportedRecordPartitioned> saved = partitionedRepository.saveAll(records);
            log.info("Batch import completed: {} records", saved.size());
            return saved;
        } catch (Exception e) {
            log.error("Failed to batch import records", e);
            throw new RuntimeException("Failed to batch import records: " + e.getMessage(), e);
        }
    }

    /**
     * Chunk 级批量幂等插入：一条 INSERT ... ON CONFLICT DO NOTHING + JDBC batch,
     * 用唯一约束 (business_key, batch_date, partition_key) 兜底幂等,
     * 避免逐条「先查后插 + 独立事务」的 N 次 round-trip。
     *
     * <p>注意:该方法运行在调用方(Spring Batch chunk)的事务里,失败时由调用方回滚并降级到逐条路径。
     *
     * @return 提交到批处理的行数(含被 ON CONFLICT 跳过的幂等命中,与旧的「幂等命中也计数」语义一致)
     */
    public int batchImportIdempotent(List<ImportedRecordPartitioned> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        String sql = "INSERT INTO imported_records_partition "
                + "(business_key, name, description, batch_date, partition_key, checksum, source_file_name, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (business_key, batch_date, partition_key) DO NOTHING";

        jdbcTemplate.batchUpdate(sql, records, records.size(), (ps, record) -> {
            String batchDate = StringUtils.hasText(record.getBatchDate())
                    ? record.getBatchDate()
                    : LocalDate.now().format(BATCH_DATE_FORMATTER);
            String partitionKey =
                    record.getPartitionKey() == null ? generatePartitionKey(batchDate) : record.getPartitionKey();
            ps.setString(1, record.getBusinessKey());
            ps.setString(2, record.getName());
            ps.setString(3, record.getDescription());
            ps.setString(4, batchDate);
            ps.setString(5, partitionKey);
            ps.setString(6, record.getChecksum());
            ps.setString(7, record.getSourceFileName());
            ps.setTimestamp(8, Timestamp.valueOf(record.getCreatedAt() == null ? now : record.getCreatedAt()));
            ps.setTimestamp(9, Timestamp.valueOf(record.getUpdatedAt() == null ? now : record.getUpdatedAt()));
        });
        log.info("Batch idempotent import submitted: {} records", records.size());
        return records.size();
    }

    /**
     * 批量回查 business_key -> id,供 chunk 批量插入后关联 trace 记录,
     * 避免逐条查询。返回 map 只含已落库的记录。
     */
    public Map<String, Long> findIdsByBatchDate(Collection<String> businessKeys, String batchDate) {
        Map<String, Long> result = new HashMap<>();
        if (businessKeys == null || businessKeys.isEmpty()) {
            return result;
        }
        String normalizedBatchDate =
                StringUtils.hasText(batchDate) ? batchDate : LocalDate.now().format(BATCH_DATE_FORMATTER);
        List<String> keys = List.copyOf(businessKeys);
        String placeholders = String.join(",", keys.stream().map(k -> "?").toList());
        String sql = "SELECT business_key, id FROM imported_records_partition "
                + "WHERE batch_date = ? AND business_key IN ("
                + placeholders + ")";
        Object[] args = new Object[keys.size() + 1];
        args[0] = normalizedBatchDate;
        for (int i = 0; i < keys.size(); i++) {
            args[i + 1] = keys.get(i);
        }
        jdbcTemplate.query(
                sql,
                rs -> {
                    result.put(rs.getString("business_key"), rs.getLong("id"));
                },
                args);
        return result;
    }

    /**
     * 查询指定分区的数据
     */
    public List<ImportedRecordPartitioned> queryByPartition(String partitionKey) {
        log.info("Querying partition: {}", partitionKey);
        return partitionedRepository.findByPartitionKey(partitionKey);
    }

    /**
     * 查询时间范围内的数据
     */
    public List<ImportedRecordPartitioned> queryByDateRange(String startDate, String endDate) {
        log.info("Querying date range: {} to {}", startDate, endDate);

        String startPartition = generatePartitionKey(startDate);
        String endPartition = generatePartitionKey(endDate);

        return partitionedRepository.findByPartitionKeyRange(startPartition, endPartition);
    }

    /**
     * 查询指定批次的记录
     */
    public List<ImportedRecordPartitioned> queryByBatchDate(String batchDate) {
        log.info("Querying batch date: {}", batchDate);
        return partitionedRepository.findByBatchDate(batchDate);
    }

    /**
     * 统计分区内的记录数
     */
    public long countByPartition(String partitionKey) {
        return partitionedRepository.countByPartitionKey(partitionKey);
    }

    /**
     * 统计批次内的记录数
     */
    public long countByBatchDate(String batchDate) {
        return partitionedRepository.countByBatchDate(batchDate);
    }

    /**
     * 生成分区键（从日期生成 yyyy_MM 格式）
     * 支持多种日期格式输入
     */
    public String generatePartitionKey(String dateString) {
        try {
            if (dateString == null || dateString.isBlank()) {
                return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM"));
            }
            // 尝试解析为日期
            if (dateString.contains("-")) {
                // yyyy-MM-dd 格式
                String[] parts = dateString.split("-");
                if (parts.length >= 2) {
                    return parts[0] + "_" + parts[1];
                }
            } else if (dateString.contains("/")) {
                // yyyy/MM/dd 格式
                String[] parts = dateString.split("/");
                if (parts.length >= 2) {
                    return parts[0] + "_" + parts[1];
                }
            } else if (dateString.length() == 8) {
                // yyyyMMdd 格式
                return dateString.substring(0, 4) + "_" + dateString.substring(4, 6);
            } else if (dateString.length() >= 7 && dateString.contains("_")) {
                // yyyy_MM 格式，直接使用
                return dateString;
            }

            log.warn("Unable to parse date format: {}", dateString);
            return dateString;
        } catch (Exception e) {
            log.error("Error generating partition key: {}", dateString, e);
            return dateString;
        }
    }

    /**
     * 获取分区统计信息
     */
    public PartitionStats getPartitionStats(String partitionKey) {
        long count = countByPartition(partitionKey);
        return new PartitionStats(partitionKey, count);
    }

    /**
     * 统计数据类
     */
    public static class PartitionStats {
        public String partitionKey;
        public long recordCount;

        public PartitionStats(String partitionKey, long recordCount) {
            this.partitionKey = partitionKey;
            this.recordCount = recordCount;
        }
    }
}
