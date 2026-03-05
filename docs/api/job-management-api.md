# Job 管理 API 文档

## 概述

本文档描述了文件批处理系统的 Job 管理 API，提供完整的任务增删改查、执行历史、重试等功能。

## 基础信息

- **Base URL**: `http://localhost:8011/api/jobs`
- **认证方式**: Spring Security
- **数据格式**: JSON
- **字符编码**: UTF-8

## API 端点

### 1. 获取任务列表

**GET** `/api/jobs`

**查询参数**:
- `status` (可选): 任务状态过滤
- `priority` (可选): 优先级过滤  
- `enabled` (可选): 启用状态过滤

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": [
    {
      "id": 1,
      "taskId": "processFileJob",
      "jobName": "processFileJob",
      "description": "文件处理任务",
      "priority": "HIGH",
      "enabled": true,
      "allowParallel": false,
      "dedupKey": "batchDate,input",
      "createdAt": "2026-03-05T10:00:00",
      "updatedAt": "2026-03-05T10:00:00",
      "trigger": {
        "id": 1,
        "taskId": "processFileJob",
        "triggerType": "CRON",
        "cronExpression": "0 0 2 * * ?",
        "fixedRateMs": null,
        "oneTimeAt": null,
        "enabled": true,
        "createdAt": "2026-03-05T10:00:00",
        "updatedAt": "2026-03-05T10:00:00"
      }
    }
  ]
}
```

### 2. 获取任务详情

**GET** `/api/jobs/{taskId}`

**路径参数**:
- `taskId`: 任务ID

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": {
    "id": 1,
    "taskId": "processFileJob",
    "jobName": "processFileJob",
    "description": "文件处理任务",
    "priority": "HIGH",
    "enabled": true,
    "allowParallel": false,
    "dedupKey": "batchDate,input",
    "createdAt": "2026-03-05T10:00:00",
    "updatedAt": "2026-03-05T10:00:00",
    "trigger": {
      "id": 1,
      "taskId": "processFileJob",
      "triggerType": "CRON",
      "cronExpression": "0 0 2 * * ?",
      "fixedRateMs": null,
      "oneTimeAt": null,
      "enabled": true,
      "createdAt": "2026-03-05T10:00:00",
      "updatedAt": "2026-03-05T10:00:00"
    }
  }
}
```

### 3. 创建新任务

**POST** `/api/jobs`

**请求体**:
```json
{
  "taskId": "newProcessJob",
  "jobName": "newProcessJob",
  "description": "新的处理任务",
  "priority": "MEDIUM",
  "allowParallel": true,
  "dedupKey": "batchDate",
  "trigger": {
    "triggerType": "CRON",
    "cronExpression": "0 30 3 * * ?",
    "enabled": true
  }
}
```

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": {
    "id": 2,
    "taskId": "newProcessJob",
    "jobName": "newProcessJob",
    "description": "新的处理任务",
    "priority": "MEDIUM",
    "enabled": true,
    "allowParallel": true,
    "dedupKey": "batchDate",
    "createdAt": "2026-03-05T11:00:00",
    "updatedAt": "2026-03-05T11:00:00",
    "trigger": {
      "id": 2,
      "taskId": "newProcessJob",
      "triggerType": "CRON",
      "cronExpression": "0 30 3 * * ?",
      "fixedRateMs": null,
      "oneTimeAt": null,
      "enabled": true,
      "createdAt": "2026-03-05T11:00:00",
      "updatedAt": "2026-03-05T11:00:00"
    }
  }
}
```

### 4. 更新任务

**PUT** `/api/jobs/{taskId}`

**路径参数**:
- `taskId`: 任务ID

**请求体**:
```json
{
  "jobName": "updatedProcessJob",
  "description": "更新后的处理任务",
  "priority": "HIGH",
  "allowParallel": false,
  "trigger": {
    "cronExpression": "0 0 4 * * ?",
    "enabled": false
  }
}
```

### 5. 删除任务

**DELETE** `/api/jobs/{taskId}`

**路径参数**:
- `taskId`: 任务ID

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": null
}
```

### 6. 启用/禁用任务

**POST** `/api/jobs/{taskId}/toggle`

**路径参数**:
- `taskId`: 任务ID

**查询参数**:
- `enabled`: true/false

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": {
    "id": 1,
    "taskId": "processFileJob",
    "enabled": false,
    "updatedAt": "2026-03-05T12:00:00"
  }
}
```

### 7. 手动触发任务执行

**POST** `/api/jobs/{taskId}/trigger`

**路径参数**:
- `taskId`: 任务ID

**请求体**:
```json
{
  "parameters": {
    "input": "/data/inbound/test.csv",
    "batchDate": "2026-03-05",
    "runMode": "normal"
  },
  "runMode": "normal",
  "batchDate": "2026-03-05",
  "rerunId": "",
  "shardIndex": 0,
  "shardTotal": 1
}
```

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": {
    "id": 123,
    "taskId": "processFileJob",
    "jobName": "processFileJob",
    "status": "RUNNING",
    "startTime": "2026-03-05T12:30:00",
    "endTime": null,
    "parameters": "{\"input\":\"/data/inbound/test.csv\"}",
    "errorMessage": null,
    "duration": null,
    "triggeredBy": "admin"
  }
}
```

### 8. 获取任务执行历史

**GET** `/api/jobs/{taskId}/executions`

**路径参数**:
- `taskId`: 任务ID

**查询参数**:
- `page`: 页码（默认0）
- `size`: 每页大小（默认20）

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": [
    {
      "id": 123,
      "taskId": "processFileJob",
      "jobName": "processFileJob",
      "status": "COMPLETED",
      "startTime": "2026-03-05T12:30:00",
      "endTime": "2026-03-05T12:45:00",
      "parameters": "{\"input\":\"/data/inbound/test.csv\"}",
      "errorMessage": null,
      "duration": 900000,
      "triggeredBy": "admin"
    }
  ]
}
```

### 9. 获取任务执行详情

**GET** `/api/jobs/executions/{executionId}`

**路径参数**:
- `executionId`: 执行ID

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": {
    "id": 123,
    "taskId": "processFileJob",
    "jobName": "processFileJob",
    "status": "COMPLETED",
    "startTime": "2026-03-05T12:30:00",
    "endTime": "2026-03-05T12:45:00",
    "parameters": {
      "input": "/data/inbound/test.csv",
      "batchDate": "2026-03-05"
    },
    "errorMessage": null,
    "duration": 900000,
    "triggeredBy": "admin",
    "exitCode": 0,
    "totalRead": 1000,
    "totalProcessed": 995,
    "totalFailed": 5
  }
}
```

### 10. 重试任务执行

**POST** `/api/jobs/executions/{executionId}/retry`

**路径参数**:
- `executionId`: 执行ID

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": {
    "id": 124,
    "taskId": "processFileJob",
    "jobName": "processFileJob",
    "status": "RUNNING",
    "startTime": "2026-03-05T13:00:00",
    "endTime": null,
    "parameters": "{\"rerunId\":\"123\"}",
    "errorMessage": null,
    "duration": null,
    "triggeredBy": "admin"
  }
}
```

### 11. 停止任务执行

**POST** `/api/jobs/executions/{executionId}/stop`

**路径参数**:
- `executionId`: 执行ID

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": null
}
```

### 12. 获取任务统计信息

**GET** `/api/jobs/statistics`

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "data": {
    "totalJobs": 10,
    "enabledJobs": 8,
    "disabledJobs": 2,
    "runningJobs": 2,
    "completedJobsToday": 15,
    "failedJobsToday": 3,
    "jobsByStatus": {
      "RUNNING": 2,
      "COMPLETED": 15,
      "FAILED": 3
    },
    "jobsByPriority": {
      "HIGH": 3,
      "MEDIUM": 4,
      "LOW": 3
    }
  }
}
```

## 错误响应格式

所有 API 在出错时都返回统一的错误格式：

```json
{
  "success": false,
  "code": "400",
  "message": "错误描述信息"
}
```

常见错误码：
- `400`: 请求参数错误
- `401`: 未授权
- `403`: 权限不足
- `404`: 资源不存在
- `500`: 服务器内部错误

## 使用示例

### 使用 curl 示例

```bash
# 获取任务列表
curl -X GET "http://localhost:8011/api/jobs" \
  -H "Authorization: Bearer your-token"

# 创建新任务
curl -X POST "http://localhost:8011/api/jobs" \
  -H "Authorization: Bearer your-token" \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": "testJob",
    "jobName": "testJob",
    "description": "测试任务",
    "priority": "MEDIUM"
  }'

# 触发任务执行
curl -X POST "http://localhost:8011/api/jobs/processFileJob/trigger" \
  -H "Authorization: Bearer your-token" \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "input": "/data/test.csv"
    }
  }'
```

### 使用 JavaScript 示例

```javascript
// 获取任务列表
const response = await fetch('/api/jobs', {
  headers: {
    'Authorization': 'Bearer ' + token,
    'Content-Type': 'application/json'
  }
});

const result = await response.json();
if (result.success) {
  console.log('任务列表:', result.data);
}

// 创建新任务
const createResponse = await fetch('/api/jobs', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer ' + token,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    taskId: 'newJob',
    jobName: 'newJob',
    description: '新任务',
    priority: 'HIGH'
  })
});

const createResult = await createResponse.json();
if (createResult.success) {
  console.log('任务创建成功:', createResult.data);
}
```

## 注意事项

1. **权限控制**: 所有 API 都需要认证，确保在请求头中包含有效的 Authorization token
2. **参数验证**: 所有请求参数都会进行验证，确保数据格式正确
3. **审计日志**: 所有操作都会记录审计日志，包括操作者、操作时间、操作内容
4. **事务安全**: 所有写操作都在事务中执行，确保数据一致性
5. **错误处理**: 统一的错误处理机制，便于前端统一处理异常情况

## 最佳实践

1. **任务管理**:
   - 定期检查任务状态
   - 合理设置任务优先级
   - 及时清理无用的任务

2. **执行监控**:
   - 监控任务执行状态
   - 及时处理失败的任务
   - 分析执行历史优化性能

3. **安全考虑**:
   - 定期轮换认证 token
   - 限制 API 调用频率
   - 记录所有操作日志

4. **性能优化**:
   - 使用分页查询大量数据
   - 合理设置查询条件
   - 缓存常用数据
