//package com.example.filebatchprocessor.controller;
//
//import com.example.filebatchprocessor.dto.*;
//import com.example.filebatchprocessor.service.JobManagementService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.Authentication;
//import org.springframework.web.bind.annotation.*;
//
//import jakarta.validation.Valid;
//import java.util.List;
//
///**
// * Job 管理 API 控制器
// * 提供任务的增删改查、执行历史、重试等功能
// */
//@RestController
//@RequestMapping("/api/jobs")
//@RequiredArgsConstructor
//@Slf4j
//public class JobManagementController {
//
//    private final JobManagementService jobManagementService;
//
//    /**
//     * 获取任务列表
//     */
//    @GetMapping
//    public ResponseEntity<ApiResponse<List<JobDefinitionDTO>>> listJobs(
//            @RequestParam(required = false) String status,
//            @RequestParam(required = false) String priority,
//            @RequestParam(required = false) Boolean enabled) {
//        List<JobDefinitionDTO> jobs = jobManagementService.listJobs(status, priority, enabled);
//        return ResponseEntity.ok(ApiResponse.success(jobs));
//    }
//
//    /**
//     * 获取任务详情
//     */
//    @GetMapping("/{taskId}")
//    public ResponseEntity<ApiResponse<JobDefinitionDTO>> getJob(@PathVariable String taskId) {
//        JobDefinitionDTO job = jobManagementService.getJob(taskId);
//        return ResponseEntity.ok(ApiResponse.success(job));
//    }
//
//    /**
//     * 创建新任务
//     */
//    @PostMapping
//    public ResponseEntity<ApiResponse<JobDefinitionDTO>> createJob(
//            @Valid @RequestBody CreateJobRequest request,
//            Authentication authentication) {
//        JobDefinitionDTO created = jobManagementService.createJob(request, authentication.getName());
//        return ResponseEntity.ok(ApiResponse.success(created));
//    }
//
//    /**
//     * 更新任务
//     */
//    @PutMapping("/{taskId}")
//    public ResponseEntity<ApiResponse<JobDefinitionDTO>> updateJob(
//            @PathVariable String taskId,
//            @Valid @RequestBody UpdateJobRequest request,
//            Authentication authentication) {
//        JobDefinitionDTO updated = jobManagementService.updateJob(taskId, request, authentication.getName());
//        return ResponseEntity.ok(ApiResponse.success(updated));
//    }
//
//    /**
//     * 删除任务
//     */
//    @DeleteMapping("/{taskId}")
//    public ResponseEntity<ApiResponse<Void>> deleteJob(
//            @PathVariable String taskId,
//            Authentication authentication) {
//        jobManagementService.deleteJob(taskId, authentication.getName());
//        return ResponseEntity.ok(ApiResponse.success(null));
//    }
//
//    /**
//     * 启用/禁用任务
//     */
//    @PostMapping("/{taskId}/toggle")
//    public ResponseEntity<ApiResponse<JobDefinitionDTO>> toggleJob(
//            @PathVariable String taskId,
//            @RequestParam boolean enabled,
//            Authentication authentication) {
//        JobDefinitionDTO updated = jobManagementService.toggleJob(taskId, enabled, authentication.getName());
//        return ResponseEntity.ok(ApiResponse.success(updated));
//    }
//
//    /**
//     * 手动触发任务执行
//     */
//    @PostMapping("/{taskId}/trigger")
//    public ResponseEntity<ApiResponse<JobExecutionDTO>> triggerJob(
//            @PathVariable String taskId,
//            @Valid @RequestBody TriggerJobRequest request,
//            Authentication authentication) {
//        JobExecutionDTO execution = jobManagementService.triggerJob(taskId, request, authentication.getName());
//        return ResponseEntity.ok(ApiResponse.success(execution));
//    }
//
//    /**
//     * 获取任务执行历史
//     */
//    @GetMapping("/{taskId}/executions")
//    public ResponseEntity<ApiResponse<List<JobExecutionDTO>>> getJobExecutions(
//            @PathVariable String taskId,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size) {
//        List<JobExecutionDTO> executions = jobManagementService.getJobExecutions(taskId, page, size);
//        return ResponseEntity.ok(ApiResponse.success(executions));
//    }
//
//    /**
//     * 获取任务执行详情
//     */
//    @GetMapping("/executions/{executionId}")
//    public ResponseEntity<ApiResponse<JobExecutionDetailDTO>> getJobExecution(
//            @PathVariable Long executionId) {
//        JobExecutionDetailDTO execution = jobManagementService.getJobExecution(executionId);
//        return ResponseEntity.ok(ApiResponse.success(execution));
//    }
//
//    /**
//     * 重试任务执行
//     */
//    @PostMapping("/executions/{executionId}/retry")
//    public ResponseEntity<ApiResponse<JobExecutionDTO>> retryJobExecution(
//            @PathVariable Long executionId,
//            Authentication authentication) {
//        JobExecutionDTO execution = jobManagementService.retryJobExecution(executionId, authentication.getName());
//        return ResponseEntity.ok(ApiResponse.success(execution));
//    }
//
//    /**
//     * 停止任务执行
//     */
//    @PostMapping("/executions/{executionId}/stop")
//    public ResponseEntity<ApiResponse<Void>> stopJobExecution(
//            @PathVariable Long executionId,
//            Authentication authentication) {
//        jobManagementService.stopJobExecution(executionId, authentication.getName());
//        return ResponseEntity.ok(ApiResponse.success(null));
//    }
//
//    /**
//     * 获取任务统计信息
//     */
//    @GetMapping("/statistics")
//    public ResponseEntity<ApiResponse<JobStatisticsDTO>> getJobStatistics() {
//        JobStatisticsDTO statistics = jobManagementService.getJobStatistics();
//        return ResponseEntity.ok(ApiResponse.success(statistics));
//    }
//}
