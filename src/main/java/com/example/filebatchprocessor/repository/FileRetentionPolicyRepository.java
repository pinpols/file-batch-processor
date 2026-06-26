package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileRetentionPolicy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileRetentionPolicyRepository extends JpaRepository<FileRetentionPolicy, Long> {

    List<FileRetentionPolicy> findByEnabledTrue();
}
