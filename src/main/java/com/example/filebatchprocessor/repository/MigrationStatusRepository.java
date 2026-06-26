package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.MigrationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MigrationStatusRepository extends JpaRepository<MigrationStatus, Long> {

    Optional<MigrationStatus> findByMigrationName(String migrationName);
}
