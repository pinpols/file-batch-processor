package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessJobInstanceRepository extends JpaRepository<BusinessJobInstance, Long> {

    Optional<BusinessJobInstance> findByJobInstanceNo(String jobInstanceNo);

    Optional<BusinessJobInstance> findBySpringBatchExecutionId(Long springBatchExecutionId);

    Optional<BusinessJobInstance> findFirstByRelatedFileIdOrderByCreatedAtDesc(Long relatedFileId);

    Page<BusinessJobInstance> findByTaskIdOrderByCreatedAtDesc(String taskId, Pageable pageable);

    List<BusinessJobInstance> findByBizDateOrderByCreatedAtDesc(String bizDate);

    List<BusinessJobInstance> findByBizDateAndStatusInOrderByCreatedAtDesc(String bizDate, Collection<String> statuses);
}
