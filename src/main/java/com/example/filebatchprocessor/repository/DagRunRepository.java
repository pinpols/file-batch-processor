package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.DagRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DagRunRepository extends JpaRepository<DagRun, Long> {
    List<DagRun> findTop50ByOrderByStartedAtDesc();
}
