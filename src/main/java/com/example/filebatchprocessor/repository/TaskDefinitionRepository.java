package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TaskDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 任务定义 Repository：管理任务配置的数据访问
 */
@Repository
public interface TaskDefinitionRepository extends JpaRepository<TaskDefinition, Long> {

    /**
     * 按任务ID查询任务定义
     */
    Optional<TaskDefinition> findByTaskId(String taskId);

    /**
     * 查询所有启用的任务定义
     */
    List<TaskDefinition> findByEnabledTrue();

    /**
     * 按优先级查询任务定义
     */
    List<TaskDefinition> findByPriority(String priority);

    /**
     * 查询允许并行执行的任务
     */
    List<TaskDefinition> findByAllowParallelTrue();
}
