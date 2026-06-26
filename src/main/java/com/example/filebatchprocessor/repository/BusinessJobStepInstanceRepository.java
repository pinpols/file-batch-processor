package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BusinessJobStepInstance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessJobStepInstanceRepository extends JpaRepository<BusinessJobStepInstance, Long> {

    List<BusinessJobStepInstance> findByJobInstanceIdOrderByStepOrderNoAsc(Long jobInstanceId);

    void deleteByJobInstanceId(Long jobInstanceId);
}
