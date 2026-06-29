package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BatchDayReplayEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchDayReplayEntryRepository extends JpaRepository<BatchDayReplayEntry, Long> {
    List<BatchDayReplayEntry> findBySessionIdOrderByIdAsc(Long sessionId);
}
