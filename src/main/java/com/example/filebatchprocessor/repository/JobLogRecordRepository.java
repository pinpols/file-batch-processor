package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.JobLogRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobLogRecordRepository extends JpaRepository<JobLogRecord, Long> {

    List<JobLogRecord> findTop50ByParamsContainingOrderByCreatedAtDesc(String params);
}
