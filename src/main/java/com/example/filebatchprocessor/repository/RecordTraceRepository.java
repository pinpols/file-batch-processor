package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.RecordTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordTraceRepository extends JpaRepository<RecordTrace, Long> {

    List<RecordTrace> findTop200ByBusinessKeyOrderByCreatedAtDesc(String businessKey);
}
