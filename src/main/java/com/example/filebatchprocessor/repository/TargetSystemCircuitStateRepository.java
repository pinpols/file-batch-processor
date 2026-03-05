package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TargetSystemCircuitState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TargetSystemCircuitStateRepository extends JpaRepository<TargetSystemCircuitState, String> {

    Optional<TargetSystemCircuitState> findByTargetSystem(String targetSystem);
}
