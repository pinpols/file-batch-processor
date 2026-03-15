package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.MigrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MigrationStatusRepository extends JpaRepository<MigrationStatus, Long> {

    Optional<MigrationStatus> findByMigrationName(String migrationName);
}
