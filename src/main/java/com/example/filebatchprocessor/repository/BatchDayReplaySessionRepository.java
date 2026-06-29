package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BatchDayReplaySession;
import com.example.filebatchprocessor.model.BatchDayReplayStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchDayReplaySessionRepository extends JpaRepository<BatchDayReplaySession, Long> {
    Optional<BatchDayReplaySession> findFirstByTenantIdAndCalendarCodeAndBizDateAndStatusIn(
            String tenantId, String calendarCode, LocalDate bizDate, Collection<BatchDayReplayStatus> statuses);

    List<BatchDayReplaySession> findTop100ByOrderByCreatedAtDesc();
}
