package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TaskTrigger;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 任务触发器 Repository：管理任务执行计划的数据访问
 */
@Repository
public interface TaskTriggerRepository extends JpaRepository<TaskTrigger, Long> {

    /**
     * 按任务ID查询触发器配置
     */
    Optional<TaskTrigger> findByTaskId(String taskId);

    /**
     * 按触发类型查询触发器
     */
    List<TaskTrigger> findByTriggerType(String triggerType);

    /**
     * 查询所有启用的触发器
     */
    List<TaskTrigger> findByEnabledTrue();

    /**
     * 按任务ID和触发器类型查询
     */
    List<TaskTrigger> findByTaskIdAndTriggerType(String taskId, String triggerType);
}
