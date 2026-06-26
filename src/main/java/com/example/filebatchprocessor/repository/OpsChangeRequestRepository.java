package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.OpsChangeRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsChangeRequestRepository extends JpaRepository<OpsChangeRequest, Long> {

    Optional<OpsChangeRequest> findByRequestNo(String requestNo);

    List<OpsChangeRequest> findTop200ByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
