package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {

    /**
     * 查询指定任务的所有依赖关系
     */
    List<TaskDependency> findByTaskId(String taskId);

    List<TaskDependency> findByTaskIdIn(List<String> taskIds);
}
