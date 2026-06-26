package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ImportedRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportedRecordRepository extends JpaRepository<ImportedRecord, Long> {
    /**
     * 检查指定 businessKey 和 batchDate 的记录是否已存在
     * 用于幂等性检查
     */
    boolean existsByBusinessKeyAndBatchDate(String businessKey, String batchDate);

    long countByBatchDate(String batchDate);

    @Query(
            "select count(r) from ImportedRecord r where r.batchDate = :batchDate and (r.name is null or trim(r.name) = '')")
    long countMissingNameByBatchDate(String batchDate);

    @Query(
            value =
                    "select count(*) from (select business_key from imported_records where batch_date = :batchDate group by business_key having count(*) > 1) t",
            nativeQuery = true)
    long countDuplicateBusinessKeysByBatchDate(String batchDate);

    List<ImportedRecord> findByBatchDateOrderByIdAsc(String batchDate);
}
