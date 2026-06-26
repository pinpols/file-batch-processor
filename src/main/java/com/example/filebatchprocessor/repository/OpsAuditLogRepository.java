package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.OpsAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsAuditLogRepository extends JpaRepository<OpsAuditLog, Long> {

    List<OpsAuditLog> findTop500ByOrderByCreatedAtDesc();
}
