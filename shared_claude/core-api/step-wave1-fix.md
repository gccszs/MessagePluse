# Core API Wave 1 Fix - 工作日志

## 完成时间
2026-03-03

## 任务概要
修复和补全 Core API 层的 Controller/Service 代码。

## 完成的工作

### 1. 修复 service/ApiKeyService.java
- **问题**: 文件在第62行截断，缺少 `revokeApiKey` 方法和类结尾括号
- **修复**: 补全 `revokeApiKey(String apiKeyId)` 方法 — 通过 apiKeyId 查找 ApiKey，设置 isActive=false 并保存
- **额外修复**: ApiKeyResponse 的 builder 字段名从 `apiKeyId` 改为 `id`（与 DTO 字段名一致）

### 2. 创建 controller/MessageController.java
- POST `/api/v1/messages`
- 注入 `MessageSendService`
- 使用 `@RequireScope("message:send")` 进行权限控制
- 接收 `@Valid @RequestBody SendMessageRequest`
- 返回 `ResponseEntity<SendMessageResponse>`

### 3. 创建 service/MessageSendService.java
- `@Service`，注入 `MessageRepository`
- `KafkaTemplate` 使用 `@Autowired(required=false)` 可选注入
- `sendMessage` 方法流程:
  1. 幂等性检查（existsByMessageId）
  2. 从 SecurityContext 提取 tenantId
  3. 构建 Message 实体（UUID id, 序列化 content/routingConfig）
  4. 保存到数据库
  5. 发送 Kafka 消息到 `messagepulse.message.send` topic
  6. 返回 SendMessageResponse

### 4. 创建 controller/MessageStatusController.java
- GET `/api/v1/messages/{messageId}/status` — 查询消息状态
- GET `/api/v1/messages/{messageId}/receipts` — 查询消息回执列表
- 注入 `MessageRepository` 和 `MessageStateRepository`
- 使用 `@RequireScope("message:read")` 进行权限控制

### 5. 创建 config/GlobalExceptionHandler.java
- `@RestControllerAdvice` 全局异常处理
- 处理异常类型及 HTTP 状态码:
  - `MessageNotFoundException` → 404
  - `DuplicateMessageException` → 409
  - `RateLimitExceededException` → 429
  - `AuthenticationException` → 401
  - `AuthorizationException` → 403
  - `MessagePulseException`（通用）→ 400
  - `Exception`（兜底）→ 500
- 统一响应格式: `{code, message, timestamp}`

### 6. 修复 service/ReceiptProcessService.java（额外修复）
- **问题**: `processReceipt` 方法参数类型为 `MessageEvent`，但 `ReceiptConsumer` 传入的是 `MessageReceipt`
- **修复**: 将参数类型改为 `MessageReceipt`，重写方法逻辑以适配 MessageReceipt 的字段结构

## 编译验证
```
mvn compile -Dcheckstyle.skip=true
BUILD SUCCESS
```

## 文件清单
| 文件 | 操作 |
|------|------|
| service/ApiKeyService.java | 修复（补全 revokeApiKey + 修复 builder 字段名） |
| controller/MessageController.java | 新建 |
| service/MessageSendService.java | 新建 |
| controller/MessageStatusController.java | 新建 |
| config/GlobalExceptionHandler.java | 新建 |
| service/ReceiptProcessService.java | 修复（参数类型 MessageEvent → MessageReceipt） |
