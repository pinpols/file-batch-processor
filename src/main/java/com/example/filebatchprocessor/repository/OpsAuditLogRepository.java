package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.OpsAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpsAuditLogRepository extends JpaRepository<OpsAuditLog, Long> {

    List<OpsAuditLog> findTop500ByOrderByCreatedAtDesc();
}

