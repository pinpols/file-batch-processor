package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BatchDayInstance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchDayInstanceRepository extends JpaRepository<BatchDayInstance, Long> {
    Optional<BatchDayInstance> findByTenantIdAndCalendarCodeAndBizDate(
            String tenantId, String calendarCode, LocalDate bizDate);

    List<BatchDayInstance> findTop100ByOrderByBizDateDesc();
}
