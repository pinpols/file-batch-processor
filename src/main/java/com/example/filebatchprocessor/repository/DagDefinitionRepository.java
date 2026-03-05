package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.DagDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DagDefinitionRepository extends JpaRepository<DagDefinition, String> {
    Optional<DagDefinition> findByDagIdAndEnabledTrue(String dagId);
}
