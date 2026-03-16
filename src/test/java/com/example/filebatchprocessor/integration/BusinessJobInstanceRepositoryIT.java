package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.repository.BusinessJobInstanceRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@ActiveProfiles("test")
class BusinessJobInstanceRepositoryIT extends PostgresContainerSupport {

    @Autowired
    private BusinessJobInstanceRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldSaveAndFindById() {
        BusinessJobInstance instance = new BusinessJobInstance();
        instance.setJobInstanceNo("JI-20260315-TEST001");
        instance.setTaskId("task-001");
        instance.setJobName("importJob");
        instance.setTriggerSource("SCHEDULER");
        instance.setStatus("TRIGGERED");

        BusinessJobInstance saved = repository.save(instance);

        Optional<BusinessJobInstance> result = repository.findById(saved.getId());

        assertTrue(result.isPresent());
        assertEquals("JI-20260315-TEST001", result.get().getJobInstanceNo());
    }

    @Test
    void shouldFindByJobInstanceNo() {
        BusinessJobInstance instance = new BusinessJobInstance();
        instance.setJobInstanceNo("JI-20260315-TEST002");
        instance.setTaskId("task-002");
        instance.setJobName("exportJob");
        instance.setTriggerSource("MANUAL");
        instance.setStatus("RUNNING");
        repository.save(instance);

        Optional<BusinessJobInstance> result = repository.findByJobInstanceNo("JI-20260315-TEST002");

        assertTrue(result.isPresent());
        assertEquals("exportJob", result.get().getJobName());
    }

    @Test
    void shouldFindBySpringBatchExecutionId() {
        BusinessJobInstance instance = new BusinessJobInstance();
        instance.setJobInstanceNo("JI-20260315-TEST003");
        instance.setTaskId("task-003");
        instance.setJobName("dataJob");
        instance.setTriggerSource("API");
        instance.setStatus("COMPLETED");
        instance.setSpringBatchExecutionId(12345L);
        repository.save(instance);

        Optional<BusinessJobInstance> result = repository.findBySpringBatchExecutionId(12345L);

        assertTrue(result.isPresent());
        assertEquals("COMPLETED", result.get().getStatus());
    }

    @Test
    void shouldFindFirstByRelatedFileIdOrderByCreatedAtDesc() {
        for (int i = 0; i < 2; i++) {
            BusinessJobInstance instance = new BusinessJobInstance();
            instance.setJobInstanceNo("JI-20260315-TEST004-" + i);
            instance.setTaskId("task-004");
            instance.setJobName("processJob");
            instance.setTriggerSource("SCHEDULER");
            instance.setStatus("COMPLETED");
            repository.save(instance);
        }

        Page<BusinessJobInstance> result = repository.findByTaskIdOrderByCreatedAtDesc("task-004", PageRequest.of(0, 10));

        assertFalse(result.isEmpty());
    }

    @Test
    void shouldFindByTaskIdOrderByCreatedAtDesc() {
        for (int i = 0; i < 5; i++) {
            BusinessJobInstance instance = new BusinessJobInstance();
            instance.setJobInstanceNo("JI-20260315-TEST005-" + i);
            instance.setTaskId("task-005");
            instance.setJobName("batchJob");
            instance.setTriggerSource("SCHEDULER");
            instance.setStatus("COMPLETED");
            repository.save(instance);
        }

        Page<BusinessJobInstance> result = repository.findByTaskIdOrderByCreatedAtDesc("task-005", PageRequest.of(0, 10));

        assertEquals(5, result.getTotalElements());
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        Optional<BusinessJobInstance> result = repository.findByJobInstanceNo("NONEXISTENT");

        assertFalse(result.isPresent());
    }
}
