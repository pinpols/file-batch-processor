package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TaskParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 任务参数 Repository：管理任务参数的数据访问
 */
@Repository
public interface TaskParameterRepository extends JpaRepository<TaskParameter, Long> {

    /**
     * 按任务ID查询所有参数
     */
    List<TaskParameter> findByTaskId(String taskId);

    /**
     * 按任务ID和参数名查询参数
     */
    Optional<TaskParameter> findByTaskIdAndParamName(String taskId, String paramName);

    /**
     * 删除任务的所有参数
     */
    void deleteByTaskId(String taskId);
}
