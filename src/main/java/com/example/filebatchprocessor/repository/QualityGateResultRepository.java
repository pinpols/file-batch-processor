package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.QualityGateResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QualityGateResultRepository extends JpaRepository<QualityGateResult, Long> {

    List<QualityGateResult> findTop50ByOrderByCreatedAtDesc();

    List<QualityGateResult> findTop200ByJobNameOrderByCreatedAtDesc(String jobName);
}
