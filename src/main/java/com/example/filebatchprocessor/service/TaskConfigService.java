package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskDependency;
import com.example.filebatchprocessor.model.TaskParameter;
import com.example.filebatchprocessor.model.TaskTrigger;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskDependencyRepository;
import com.example.filebatchprocessor.repository.TaskParameterRepository;
import com.example.filebatchprocessor.repository.TaskTriggerRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务配置服务：从数据库加载任务定义、触发器、参数
 * 提供统一的任务配置查询接口
 */
@Slf4j
@Service
@Transactional
public class TaskConfigService {

    private final TaskDefinitionRepository taskDefinitionRepository;
    private final TaskTriggerRepository taskTriggerRepository;
    private final TaskParameterRepository taskParameterRepository;
    private final TaskDependencyRepository taskDependencyRepository;

    public TaskConfigService(
            TaskDefinitionRepository taskDefinitionRepository,
            TaskTriggerRepository taskTriggerRepository,
            TaskParameterRepository taskParameterRepository,
            TaskDependencyRepository taskDependencyRepository) {
        this.taskDefinitionRepository = taskDefinitionRepository;
        this.taskTriggerRepository = taskTriggerRepository;
        this.taskParameterRepository = taskParameterRepository;
        this.taskDependencyRepository = taskDependencyRepository;
    }

    /**
     * 获取所有启用的任务定义
     */
    public List<TaskDefinition> getAllEnabledTasks() {
        return taskDefinitionRepository.findByEnabledTrue();
    }

    /**
     * 按任务ID获取任务定义
     */
    public TaskDefinition getTaskDefinition(String taskId) {
        return taskDefinitionRepository
                .findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    /**
     * 获取任务的触发器配置
     */
    public TaskTrigger getTaskTrigger(String taskId) {
        return taskTriggerRepository
                .findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Trigger not found for task: " + taskId));
    }

    /**
     * 获取任务的所有参数
     */
    public List<TaskParameter> getTaskParameters(String taskId) {
        return taskParameterRepository.findByTaskId(taskId);
    }

    /**
     * 获取任务参数的键值映射
     */
    public Map<String, String> getTaskParametersAsMap(String taskId) {
        List<TaskParameter> parameters = getTaskParameters(taskId);
        Map<String, String> paramMap = new HashMap<>();
        for (TaskParameter param : parameters) {
            paramMap.put(param.getParamName(), param.getParamValue());
        }
        return paramMap;
    }

    /**
     * 获取任务的所有依赖任务 ID 列表
     */
    public List<String> getTaskDependencies(String taskId) {
        return taskDependencyRepository.findByTaskId(taskId).stream()
                .map(TaskDependency::getDependsOnTaskId)
                .toList();
    }

    public List<TaskDependency> getTaskDependencyConfigs(String taskId) {
        return taskDependencyRepository.findByTaskId(taskId);
    }

    /**
     * 获取指定参数值
     */
    public String getTaskParameter(String taskId, String paramName) {
        return taskParameterRepository
                .findByTaskIdAndParamName(taskId, paramName)
                .map(TaskParameter::getParamValue)
                .orElse(null);
    }

    /**
     * 创建或更新任务定义
     */
    public TaskDefinition saveTaskDefinition(TaskDefinition taskDefinition) {
        log.info("Saving task definition: taskId={}", taskDefinition.getTaskId());
        return taskDefinitionRepository.save(taskDefinition);
    }

    /**
     * 创建或更新任务触发器
     */
    public TaskTrigger saveTaskTrigger(TaskTrigger taskTrigger) {
        log.info("Saving task trigger: taskId={}", taskTrigger.getTaskId());
        return taskTriggerRepository.save(taskTrigger);
    }

    /**
     * 创建或更新任务参数
     */
    public TaskParameter saveTaskParameter(TaskParameter taskParameter) {
        log.info(
                "Saving task parameter: taskId={}, paramName={}",
                taskParameter.getTaskId(),
                taskParameter.getParamName());
        return taskParameterRepository.save(taskParameter);
    }

    /**
     * 删除任务及其关联的触发器和参数
     */
    public void deleteTask(String taskId) {
        log.info("Deleting task: taskId={}", taskId);
        taskDefinitionRepository.deleteById(taskDefinitionRepository
                .findByTaskId(taskId)
                .map(TaskDefinition::getId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId)));
        taskParameterRepository.deleteByTaskId(taskId);
    }

    /**
     * 查询所有 CRON 类型的任务
     */
    public List<TaskTrigger> getCronTasks() {
        return taskTriggerRepository.findByTriggerType("CRON");
    }

    /**
     * 查询所有 FIXED_RATE 类型的任务
     */
    public List<TaskTrigger> getFixedRateTasks() {
        return taskTriggerRepository.findByTriggerType("FIXED_RATE");
    }

    /**
     * 启用或禁用任务
     */
    public void enableTask(String taskId, boolean enabled) {
        TaskDefinition task = getTaskDefinition(taskId);
        task.setEnabled(enabled);
        taskDefinitionRepository.save(task);
        log.info("Task enabled={}: taskId={}", enabled, taskId);
    }
}
