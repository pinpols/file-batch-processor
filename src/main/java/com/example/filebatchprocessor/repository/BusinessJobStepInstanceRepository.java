package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BusinessJobStepInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessJobStepInstanceRepository extends JpaRepository<BusinessJobStepInstance, Long> {

    List<BusinessJobStepInstance> findByJobInstanceIdOrderByStepOrderNoAsc(Long jobInstanceId);

    void deleteByJobInstanceId(Long jobInstanceId);
}
