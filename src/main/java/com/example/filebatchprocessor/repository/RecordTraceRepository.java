package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.RecordTrace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecordTraceRepository extends JpaRepository<RecordTrace, Long> {

    List<RecordTrace> findTop200ByBusinessKeyOrderByCreatedAtDesc(String businessKey);
}
