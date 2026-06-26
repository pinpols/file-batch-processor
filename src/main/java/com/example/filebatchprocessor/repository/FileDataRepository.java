package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileData;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileDataRepository extends JpaRepository<FileData, Long> {
    // Custom query methods can be added here if needed

    List<FileData> findByStatus(String status);
}
