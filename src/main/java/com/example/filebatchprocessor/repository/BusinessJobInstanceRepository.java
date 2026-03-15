package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessJobInstanceRepository extends JpaRepository<BusinessJobInstance, Long> {

    Optional<BusinessJobInstance> findByJobInstanceNo(String jobInstanceNo);

    Optional<BusinessJobInstance> findBySpringBatchExecutionId(Long springBatchExecutionId);

    Page<BusinessJobInstance> findByTaskIdOrderByCreatedAtDesc(String taskId, Pageable pageable);
}
