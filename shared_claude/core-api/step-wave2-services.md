# Core API Wave 2 Services - 工作日志

## 完成时间
2026-03-03

## 任务概要
Wave 2: 限流服务 + 计费统计 + 模板系统 + Skill 管理 API

## 完成的工作

### T19 - 限流服务

#### 1. src/main/resources/scripts/token-bucket.lua
- 令牌桶算法 Lua 脚本
- 参数：key, max_tokens, refill_rate, requested, timestamp
- 原子操作：计算补充令牌 → 检查是否足够 → 扣减 → 返回 allowed (0/1)
- 自动过期 3600s

#### 2. service/RateLimiterService.java
- 三级限流：API Key 级 / 租户级 / 渠道级
- `checkApiKeyRateLimit(apiKeyId, rateLimit)` - API Key 维度
- `checkTenantRateLimit(tenantId, rateLimit)` - 租户维度
- `checkChannelRateLimit(tenantId, channelType, rateLimit)` - 渠道维度
- Redis 可选注入（`@Autowired(required=false)`），无 Redis 时跳过限流
- 使用 `DefaultRedisScript` 执行 Lua 脚本

### T20 - 计费统计

#### 3. service/BillingService.java
- `recordBilling(tenantId, messageId, channelType, cost)` - 记录计费
- `getRecords(tenantId, start, end)` - 查询计费记录
- `getStats(tenantId, start, end)` - 统计汇总（总消息数、总费用、按渠道分组）

#### 4. controller/BillingController.java
- GET `/api/v1/billing/stats?start=...&end=...` - 查询统计
- GET `/api/v1/billing/records?start=...&end=...` - 查询记录
- `@RequireScope("billing:read")` 权限控制
- 从 SecurityContext 提取 tenantId

### T21 - 模板系统

#### 5. service/TemplateService.java
- `createTemplate(template)` - 创建模板
- `getTemplate(id)` - 获取模板
- `listTemplates(tenantId, channelType)` - 列表查询
- `updateTemplate(id, updated)` - 更新模板
- `deleteTemplate(id)` - 删除模板
- `renderTemplate(content, variables)` - 渲染模板（`{{key}}` → value 替换）

#### 6. controller/TemplateController.java
- POST `/api/v1/templates` - 创建
- GET `/api/v1/templates/{id}` - 获取
- GET `/api/v1/templates?channelType=...` - 列表
- PUT `/api/v1/templates/{id}` - 更新
- DELETE `/api/v1/templates/{id}` - 删除
- 读操作 `@RequireScope("template:read")`，写操作 `@RequireScope("template:write")`

### T25 - Skill 管理 API

#### 7. service/SkillManagementService.java
- `registerSkillInstance(skillConfigId, instanceId, endpoint)` - 注册实例
- `discoverSkills(tenantId, channelType)` - 发现可用 Skill
- `updateHeartbeat(instanceId)` - 心跳更新
- `enableSkill(skillConfigId)` - 启用 Skill
- `disableSkill(skillConfigId)` - 禁用 Skill
- `getActiveInstances(skillConfigId)` - 获取活跃实例

#### 8. controller/SkillController.java
- GET `/api/v1/skills?channelType=...` - 列表查询
- POST `/api/v1/skills/{skillConfigId}/enable` - 启用
- POST `/api/v1/skills/{skillConfigId}/disable` - 禁用
- POST `/api/v1/skills/heartbeat` - 心跳上报
- 权限控制：read/manage/heartbeat 分级

## 编译验证
```
mvn compile -Dcheckstyle.skip=true
Compiling 87 source files
BUILD SUCCESS
```

## 文件清单
| 文件 | 操作 |
|------|------|
| src/main/resources/scripts/token-bucket.lua | 新建 |
| service/RateLimiterService.java | 新建 |
| service/BillingService.java | 新建 |
| controller/BillingController.java | 新建 |
| service/TemplateService.java | 新建 |
| controller/TemplateController.java | 新建 |
| service/SkillManagementService.java | 新建 |
| controller/SkillController.java | 新建 |
