package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ReceptionGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceptionGroupRepository extends JpaRepository<ReceptionGroup, Long> {
    Optional<ReceptionGroup> findByManifestId(String manifestId);

    List<ReceptionGroup> findByStatus(String status);
}
