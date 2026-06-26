package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.DagNode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DagNodeRepository extends JpaRepository<DagNode, Long> {
    List<DagNode> findByDagIdAndEnabledTrueOrderByNodeOrderAscIdAsc(String dagId);
}
