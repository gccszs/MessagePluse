# API 设计

## API 概览

MessagePulse 2.0 提供 RESTful API，遵循 REST 设计原则，支持 JSON 格式的请求和响应。

### API 基础信息

- **Base URL**: `http://api.messagepulse.example.com`
- **API 版本**: `/api/v1`
- **认证方式**: API Key (Bearer Token)
- **数据格式**: JSON
- **字符编码**: UTF-8

### API 分类

```
/api/v1
├── /messages          # 消息管理
├── /api-keys          # API Key 管理
├── /skills            # Channel Skill 管理
├── /templates         # 消息模板管理
├── /recipients        # 用户管理
└── /billing           # 计费统计
```

## 认证授权

### API Key 认证

所有 API 请求都需要在 HTTP Header 中携带 API Key：

```http
Authorization: Bearer ak_xxx.keySecret
```

**示例**：

```bash
curl -X POST https://api.messagepulse.example.com/api/v1/messages \
  -H "Authorization: Bearer ak_abc123.xyz789" \
  -H "Content-Type: application/json" \
  -d '{"content": "Hello"}'
```

### 权限范围（Scopes）

API Key 支持细粒度的权限控制：

| Scope | 说明 |
|-------|------|
| `message:send` | 发送消息 |
| `message:query` | 查询消息状态 |
| `message:revoke` | 撤回消息 |
| `apikey:manage` | 管理 API Key |
| `skill:manage` | 管理 Channel Skill |
| `template:manage` | 管理消息模板 |
| `billing:view` | 查看计费信息 |

## 消息管理 API

### 1. 发送消息

**接口**：`POST /api/v1/messages`

**权限**：`message:send`

**请求示例**：

```json
{
  "content": {
    "template": {
      "id": "verification_code_v1",
      "variables": {
        "code": "123456",
        "ttl": "5分钟"
      }
    }
  },
  "routing": {
    "mode": "explicit",
    "channels": ["sms", "feishu"],
    "strategy": "all"
  },
  "priority": "high",
  "recipient": {
    "userId": "user_123",
    "phone": "13800138000",
    "feishuUserId": "ou_xxx"
  }
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | Object | 是 | 消息内容 |
| content.template | Object | 否 | 模板引用 |
| content.template.id | String | 否 | 模板ID |
| content.template.variables | Object | 否 | 模板变量 |
| content.title | String | 否 | 消息标题 |
| content.body | String | 否 | 消息正文 |
| routing | Object | 是 | 路由配置 |
| routing.mode | String | 是 | 路由模式: explicit/implicit/auto |
| routing.channels | Array | 是 | 目标渠道列表 |
| routing.strategy | String | 是 | 投递策略: all/any/priority_order |
| priority | String | 是 | 优先级: high/normal/low |
| recipient | Object | 是 | 接收者信息 |
| recipient.userId | String | 是 | 用户ID |
| recipient.phone | String | 否 | 手机号 |
| recipient.email | String | 否 | 邮箱 |
| recipient.feishuUserId | String | 否 | 飞书用户ID |

**响应示例**（201 Created）：

```json
{
  "messageId": "msg_20260302_123456",
  "status": "PENDING",
  "createdAt": "2026-03-02T10:30:00Z"
}
```

**错误响应**：

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Invalid recipient phone number",
    "details": {
      "field": "recipient.phone",
      "value": "invalid"
    }
  }
}
```

### 2. 查询消息状态

**接口**：`GET /api/v1/messages/{messageId}/status`

**权限**：`message:query`

**响应示例**：

```json
{
  "messageId": "msg_20260302_123456",
  "status": "DELIVERED",
  "overallStatus": "PARTIAL_SUCCESS",
  "channelStatuses": {
    "sms": {
      "status": "DELIVERED",
      "sentAt": "2026-03-02T10:30:01Z",
      "deliveredAt": "2026-03-02T10:30:03Z",
      "durationMs": 2000,
      "externalMessageId": "sms_ext_123"
    },
    "feishu": {
      "status": "FAILED",
      "errorCode": "RATE_LIMIT",
      "errorMessage": "频率限制",
      "retryable": true,
      "sentAt": "2026-03-02T10:30:01Z"
    }
  },
  "createdAt": "2026-03-02T10:30:00Z",
  "updatedAt": "2026-03-02T10:30:03Z"
}
```

### 3. 查询消息回执

**接口**：`GET /api/v1/messages/{messageId}/receipts`

**权限**：`message:query`

**响应示例**：

```json
{
  "messageId": "msg_20260302_123456",
  "receipts": [
    {
      "channel": "sms",
      "status": "DELIVERED",
      "sentAt": "2026-03-02T10:30:01Z",
      "deliveredAt": "2026-03-02T10:30:03Z",
      "durationMs": 2000,
      "externalMessageId": "sms_ext_123"
    },
    {
      "channel": "feishu",
      "status": "FAILED",
      "errorCode": "RATE_LIMIT",
      "errorMessage": "频率限制",
      "retryable": true,
      "sentAt": "2026-03-02T10:30:01Z"
    }
  ]
}
```

### 4. 撤回消息

**接口**：`POST /api/v1/messages/{messageId}/revoke`

**权限**：`message:revoke`

**请求示例**：

```json
{
  "reason": "用户取消操作"
}
```

**响应示例**：

```json
{
  "messageId": "msg_20260302_123456",
  "status": "REVOKED",
  "revokedAt": "2026-03-02T10:31:00Z",
  "channelResults": {
    "sms": {
      "revoked": false,
      "reason": "已送达，无法撤回"
    },
    "feishu": {
      "revoked": true,
      "revokedAt": "2026-03-02T10:31:01Z"
    }
  }
}
```

### 5. 批量查询消息

**接口**：`GET /api/v1/messages/query`

**权限**：`message:query`

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| messageId | String | 否 | 消息ID（精确查询） |
| recipient | String | 否 | 接收者（手机号/邮箱） |
| status | String | 否 | 消息状态 |
| channel | String | 否 | 渠道 |
| startDate | String | 否 | 开始时间（ISO 8601） |
| endDate | String | 否 | 结束时间（ISO 8601） |
| page | Integer | 否 | 页码（默认 1） |
| size | Integer | 否 | 每页大小（默认 20，最大 100） |
| sort | String | 否 | 排序字段（createdAt/sentAt） |
| order | String | 否 | 排序方向（asc/desc） |

**请求示例**：

```
GET /api/v1/messages/query?status=FAILED&startDate=2026-03-01T00:00:00Z&endDate=2026-03-02T23:59:59Z&page=1&size=20
```

**响应示例**：

```json
{
  "total": 1000,
  "page": 1,
  "size": 20,
  "messages": [
    {
      "messageId": "msg_xxx",
      "status": "FAILED",
      "content": {
        "title": "告警通知",
        "body": "..."
      },
      "recipient": {
        "userId": "user_123",
        "phone": "13800138000"
      },
      "channels": ["sms", "email"],
      "createdAt": "2026-03-02T10:00:00Z",
      "errorMessage": "Rate limit exceeded"
    }
  ]
}
```

## API Key 管理 API

### 1. 创建 API Key

**接口**：`POST /api/v1/api-keys`

**权限**：`apikey:manage`

**请求示例**：

```json
{
  "name": "Production Key",
  "tenantId": "tenant_123",
  "scopes": ["message:send", "message:query"],
  "allowedChannels": ["sms", "email"],
  "rateLimit": {
    "requestsPerMinute": 100,
    "requestsPerDay": 10000
  },
  "expiresAt": "2027-03-02T00:00:00Z"
}
```

**响应示例**：

```json
{
  "keyId": "ak_abc123...",
  "keySecret": "xyz789...",
  "tenantId": "tenant_123",
  "name": "Production Key",
  "scopes": ["message:send", "message:query"],
  "allowedChannels": ["sms", "email"],
  "createdAt": "2026-03-02T10:00:00Z",
  "expiresAt": "2027-03-02T00:00:00Z"
}
```

**注意**：`keySecret` 仅在创建时返回一次，请妥善保管。

### 2. 列出 API Keys

**接口**：`GET /api/v1/api-keys`

**权限**：`apikey:manage`

**响应示例**：

```json
{
  "apiKeys": [
    {
      "keyId": "ak_abc123",
      "name": "Production Key",
      "tenantId": "tenant_123",
      "scopes": ["message:send", "message:query"],
      "active": true,
      "createdAt": "2026-03-02T10:00:00Z",
      "lastUsedAt": "2026-03-02T15:30:00Z"
    }
  ]
}
```

### 3. 撤销 API Key

**接口**：`DELETE /api/v1/api-keys/{keyId}`

**权限**：`apikey:manage`

**响应**：`204 No Content`

## Channel Skill 管理 API

### 1. 列出所有 Skills

**接口**：`GET /api/v1/skills`

**权限**：`skill:manage`

**响应示例**：

```json
{
  "skills": [
    {
      "channel": "sms",
      "displayName": "短信",
      "enabled": true,
      "consumerGroup": "messagepulse.sms",
      "instances": [
        {
          "skillId": "sms-skill-1",
          "address": "192.168.1.10",
          "port": 8081,
          "status": "ACTIVE",
          "lastHeartbeat": "2026-03-02T10:30:00Z"
        }
      ]
    },
    {
      "channel": "feishu",
      "displayName": "飞书",
      "enabled": true,
      "consumerGroup": "messagepulse.feishu",
      "instances": [
        {
          "skillId": "feishu-skill-1",
          "address": "192.168.1.11",
          "port": 8082,
          "status": "ACTIVE",
          "lastHeartbeat": "2026-03-02T10:30:05Z"
        }
      ]
    }
  ]
}
```

### 2. 启用 Skill

**接口**：`POST /api/v1/skills/{channel}/enable`

**权限**：`skill:manage`

**响应**：`200 OK`

### 3. 停用 Skill

**接口**：`POST /api/v1/skills/{channel}/disable`

**权限**：`skill:manage`

**响应**：`200 OK`

## 消息模板 API

### 1. 创建模板

**接口**：`POST /api/v1/templates`

**权限**：`template:manage`

**请求示例**：

```json
{
  "templateId": "verification_code_v1",
  "name": "验证码模板",
  "category": "verification",
  "contents": {
    "zh-CN": {
      "sms": {
        "text": "您的验证码是 {{code}}，有效期 {{ttl}}"
      },
      "email": {
        "subject": "验证码",
        "text": "您的验证码是 {{code}}",
        "html": "<p>您的验证码是 <strong>{{code}}</strong></p>"
      }
    }
  }
}
```

**响应**：`201 Created`

### 2. 列出模板

**接口**：`GET /api/v1/templates`

**权限**：`template:manage`

**查询参数**：
- `category`: 模板分类
- `active`: 是否激活

**响应示例**：

```json
{
  "templates": [
    {
      "templateId": "verification_code_v1",
      "name": "验证码模板",
      "category": "verification",
      "active": true,
      "version": 1,
      "createdAt": "2026-03-02T10:00:00Z"
    }
  ]
}
```

## 计费统计 API

### 1. 查询计费汇总

**接口**：`GET /api/v1/billing/summary`

**权限**：`billing:view`

**查询参数**：
- `tenantId`: 租户ID
- `startDate`: 开始日期
- `endDate`: 结束日期

**响应示例**：

```json
{
  "tenantId": "tenant_123",
  "startDate": "2026-03-01",
  "endDate": "2026-03-31",
  "totalCost": 1234.56,
  "totalMessages": 100000,
  "currency": "CNY",
  "breakdownByChannel": {
    "sms": {
      "count": 50000,
      "cost": 500.00
    },
    "email": {
      "count": 40000,
      "cost": 200.00
    },
    "feishu": {
      "count": 10000,
      "cost": 534.56
    }
  },
  "breakdownByDay": [
    {
      "date": "2026-03-01",
      "count": 3000,
      "cost": 35.00
    }
  ]
}
```

## 错误码

### 标准错误响应格式

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": {
      "field": "fieldName",
      "value": "invalidValue"
    }
  }
}
```

### 错误码列表

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 400 | INVALID_REQUEST | 请求参数错误 |
| 401 | UNAUTHORIZED | 未认证 |
| 403 | FORBIDDEN | 无权限 |
| 404 | NOT_FOUND | 资源不存在 |
| 409 | CONFLICT | 资源冲突（如重复创建） |
| 429 | RATE_LIMIT_EXCEEDED | 超过限流 |
| 500 | INTERNAL_ERROR | 服务器内部错误 |
| 503 | SERVICE_UNAVAILABLE | 服务不可用 |

### 业务错误码

| 错误码 | 说明 |
|--------|------|
| INVALID_API_KEY | API Key 无效 |
| API_KEY_EXPIRED | API Key 已过期 |
| INSUFFICIENT_QUOTA | 配额不足 |
| INVALID_CHANNEL | 无效的渠道 |
| CHANNEL_NOT_ENABLED | 渠道未启用 |
| INVALID_RECIPIENT | 无效的接收者 |
| MESSAGE_NOT_FOUND | 消息不存在 |
| MESSAGE_ALREADY_SENT | 消息已发送，无法撤回 |
| TEMPLATE_NOT_FOUND | 模板不存在 |

## API 限流

### 限流维度

1. **API Key 级别**：每个 API Key 独立限流
2. **租户级别**：租户总体限流
3. **渠道级别**：单个渠道限流

### 限流响应

当触发限流时，返回 `429 Too Many Requests`：

```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded",
    "details": {
      "limit": 100,
      "window": "1 minute",
      "retryAfter": 30
    }
  }
}
```

**响应头**：

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1709370000
Retry-After: 30
```

## API 版本管理

### 版本策略

- 当前版本：`/api/v1`
- 新版本：`/api/v2`（未来）

### 版本兼容性

- 向后兼容：新增字段不影响旧版本
- 破坏性变更：发布新版本 API

## 与其他知识文档的关系

- **系统架构** → `02-architecture.md`：API 在接入层的位置
- **数据库设计** → `04-database-design.md`：API 操作的数据表
- **安全设计** → `08-security.md`：API Key 认证机制
- **Channel Skills** → `09-channel-skills.md`：Skill 管理 API
