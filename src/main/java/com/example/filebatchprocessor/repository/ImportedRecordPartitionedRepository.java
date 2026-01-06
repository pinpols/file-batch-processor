package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImportedRecordPartitionedRepository extends JpaRepository<ImportedRecordPartitioned, Long> {

    /**
     * 根据 business_key 和 batch_date 查找记录
     */
    Optional<ImportedRecordPartitioned> findByBusinessKeyAndBatchDate(String businessKey, String batchDate);

    /**
     * 按批次日期查找所有记录
     */
    List<ImportedRecordPartitioned> findByBatchDate(String batchDate);

    /**
     * 按分区键查找（支持按年月分区）
     */
    List<ImportedRecordPartitioned> findByPartitionKey(String partitionKey);

    /**
     * 分区范围查询
     */
    @Query("SELECT r FROM ImportedRecordPartitioned r WHERE r.partitionKey >= :startPartition AND r.partitionKey <= :endPartition")
    List<ImportedRecordPartitioned> findByPartitionKeyRange(String startPartition, String endPartition);

    /**
     * 查找指定时间范围内的记录
     */
    @Query("SELECT r FROM ImportedRecordPartitioned r WHERE r.createdAt BETWEEN :startTime AND :endTime")
    List<ImportedRecordPartitioned> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定批次的记录数
     */
    long countByBatchDate(String batchDate);

    /**
     * 统计指定分区的记录数
     */
    long countByPartitionKey(String partitionKey);
}
