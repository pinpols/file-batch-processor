package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FieldMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FieldMappingRepository extends JpaRepository<FieldMapping, Long> {

    List<FieldMapping> findByFeedIdAndEnabledTrueOrderByOrderNoAsc(String feedId);
}
