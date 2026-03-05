package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.DagNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DagNodeRepository extends JpaRepository<DagNode, Long> {
    List<DagNode> findByDagIdAndEnabledTrueOrderByNodeOrderAscIdAsc(String dagId);
}
