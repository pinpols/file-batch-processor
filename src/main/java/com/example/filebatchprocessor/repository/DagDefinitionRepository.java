package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.DagDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DagDefinitionRepository extends JpaRepository<DagDefinition, String> {
    Optional<DagDefinition> findByDagIdAndEnabledTrue(String dagId);
}
