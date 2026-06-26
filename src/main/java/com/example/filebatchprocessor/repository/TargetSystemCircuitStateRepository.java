package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TargetSystemCircuitState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TargetSystemCircuitStateRepository extends JpaRepository<TargetSystemCircuitState, String> {

    Optional<TargetSystemCircuitState> findByTargetSystem(String targetSystem);
}
