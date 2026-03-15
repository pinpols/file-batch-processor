package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileRetentionPolicyRepository extends JpaRepository<FileRetentionPolicy, Long> {

    List<FileRetentionPolicy> findByEnabledTrue();
}
