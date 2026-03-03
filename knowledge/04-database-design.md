# 数据库设计

## 数据库概览

MessagePulse 2.0 使用 MySQL 8.x 作为主要的持久化存储，共设计 7 张核心表。

### 表结构总览

```
messagepulse 数据库
├── messages              # 消息主表
├── message_states        # 消息状态表
├── api_keys              # API Key 表
├── skill_configs         # Skill 配置表
├── skill_instances       # Skill 实例表
├── billing_records       # 计费记录表
├── message_templates     # 消息模板表
└── recipients            # 用户接收者信息表（传统功能）
```

## 核心表设计

### 1. messages（消息主表）

存储消息的基本信息和内容。

```sql
CREATE TABLE messages (
    message_id VARCHAR(64) PRIMARY KEY COMMENT '消息唯一ID',
    tenant_id VARCHAR(100) NOT NULL COMMENT '租户ID',
    status VARCHAR(50) NOT NULL COMMENT '消息状态',
    content JSON NOT NULL COMMENT '消息内容',
    routing JSON NOT NULL COMMENT '路由配置',
    priority VARCHAR(20) NOT NULL COMMENT '优先级: high/normal/low',
    recipient JSON NOT NULL COMMENT '接收者信息',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    expires_at TIMESTAMP NULL COMMENT '过期时间',

    INDEX idx_tenant_id (tenant_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息主表';
```

**字段说明**：

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| message_id | VARCHAR(64) | 消息唯一标识 | msg_20260302_123456 |
| tenant_id | VARCHAR(100) | 租户ID，用于多租户隔离 | tenant_123 |
| status | VARCHAR(50) | 消息状态 | PENDING, PROCESSING, DELIVERED |
| content | JSON | 消息内容（标题、正文、模板等） | {"title": "告警", "body": "..."} |
| routing | JSON | 路由配置（渠道、策略） | {"channels": ["sms", "email"]} |
| priority | VARCHAR(20) | 优先级 | high, normal, low |
| recipient | JSON | 接收者信息 | {"userId": "u1", "phone": "138..."} |

**JSON 字段结构**：

```json
// content 字段
{
  "template": {
    "id": "verification_code_v1",
    "variables": {"code": "123456"}
  },
  "title": "验证码",
  "body": "您的验证码是 123456",
  "formats": {
    "text": "验证码: 123456",
    "html": "<p>验证码: <strong>123456</strong></p>"
  }
}

// routing 字段
{
  "mode": "explicit",
  "channels": ["sms", "feishu"],
  "strategy": "all"
}

// recipient 字段
{
  "userId": "user_123",
  "phone": "13800138000",
  "email": "user@example.com",
  "feishuUserId": "ou_xxx"
}
```

**索引设计**：

- `idx_tenant_id`：按租户查询消息
- `idx_status`：按状态查询（如查询失败消息）
- `idx_created_at`：按时间范围查询
- `idx_tenant_status`：组合索引，租户 + 状态查询

### 2. message_states（消息状态表）

存储消息的详细状态信息，支持重试和错误追踪。

```sql
CREATE TABLE message_states (
    message_id VARCHAR(64) PRIMARY KEY COMMENT '消息ID',
    tenant_id VARCHAR(100) NOT NULL COMMENT '租户ID',
    status VARCHAR(50) NOT NULL COMMENT '消息状态',
    channel_statuses JSON COMMENT '各渠道状态',
    error_message TEXT COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retries INT DEFAULT 3 COMMENT '最大重试次数',
    last_retry_at TIMESTAMP NULL COMMENT '最后重试时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    expires_at TIMESTAMP NULL COMMENT '过期时间',

    FOREIGN KEY (message_id) REFERENCES messages(message_id),
    INDEX idx_status (status),
    INDEX idx_retry (status, retry_count, last_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息状态表';
```

**channel_statuses JSON 结构**：

```json
{
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
}
```

**状态枚举**：

```java
public enum MessageStatus {
    PENDING,         // 消息已创建，等待处理
    PROCESSING,      // 消息正在处理
    SENT_TO_CHANNEL, // 消息已发送到渠道
    DELIVERED,       // 消息已送达
    FAILED,          // 消息发送失败
    REVOKED,         // 消息已撤回
    EXPIRED,         // 消息已过期
    DEAD_LETTER      // 消息在死信队列中
}
```

### 3. api_keys（API Key 表）

存储 API Key 信息，用于认证和授权。

```sql
CREATE TABLE api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    key_id VARCHAR(64) UNIQUE NOT NULL COMMENT 'API Key ID',
    key_secret VARCHAR(255) NOT NULL COMMENT 'API Key Secret (BCrypt加密)',
    tenant_id VARCHAR(100) NOT NULL COMMENT '租户ID',
    name VARCHAR(100) NOT NULL COMMENT 'Key名称',
    description TEXT COMMENT '描述',
    scopes JSON NOT NULL COMMENT '权限范围',
    allowed_channels JSON NOT NULL COMMENT '允许的渠道',
    rate_limit JSON COMMENT '限流配置',
    active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    expires_at TIMESTAMP NULL COMMENT '过期时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_used_at TIMESTAMP NULL COMMENT '最后使用时间',
    created_by VARCHAR(100) COMMENT '创建者',

    INDEX idx_tenant_id (tenant_id),
    INDEX idx_key_id (key_id),
    INDEX idx_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key表';
```

**JSON 字段结构**：

```json
// scopes 字段
["message:send", "message:query", "message:revoke"]

// allowed_channels 字段
["sms", "email", "feishu"]

// rate_limit 字段
{
  "requestsPerMinute": 100,
  "requestsPerHour": 5000,
  "requestsPerDay": 100000
}
```

**API Key 格式**：

- `key_id`: `ak_` + 随机字符串（32位）
- `key_secret`: BCrypt 加密存储

### 4. skill_configs（Skill 配置表）

存储 Channel Skill 的静态配置信息。

```sql
CREATE TABLE skill_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    channel_name VARCHAR(100) UNIQUE NOT NULL COMMENT '渠道名称',
    display_name VARCHAR(100) NOT NULL COMMENT '显示名称',
    consumer_group VARCHAR(100) NOT NULL COMMENT 'Kafka消费者组',
    topic VARCHAR(100) NOT NULL COMMENT 'Kafka Topic',
    filter_expression TEXT NOT NULL COMMENT '消息过滤表达式',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    channel_config JSON COMMENT '渠道特定配置',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_channel_name (channel_name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill配置表';
```

**示例数据**：

```sql
INSERT INTO skill_configs (channel_name, display_name, consumer_group, topic, filter_expression, channel_config)
VALUES
('sms', '短信', 'messagepulse.sms', 'messagepulse.messages', 'channels contains "sms"', '{"provider": "aliyun"}'),
('email', '邮件', 'messagepulse.email', 'messagepulse.messages', 'channels contains "email"', '{"smtp": "smtp.example.com"}'),
('feishu', '飞书', 'messagepulse.feishu', 'messagepulse.messages', 'channels contains "feishu"', '{"appId": "xxx", "appSecret": "yyy"}');
```

### 5. skill_instances（Skill 实例表）

存储 Channel Skill 的运行实例信息，用于服务发现和健康检查。

```sql
CREATE TABLE skill_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    skill_id VARCHAR(255) UNIQUE NOT NULL COMMENT 'Skill实例ID',
    channel VARCHAR(100) NOT NULL COMMENT '渠道名称',
    address VARCHAR(255) NOT NULL COMMENT 'IP地址',
    port INT NOT NULL COMMENT '端口',
    status VARCHAR(50) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '最后心跳时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_channel (channel),
    INDEX idx_status (status),
    INDEX idx_last_heartbeat (last_heartbeat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill实例表';
```

**心跳机制**：

- Skill 启动时注册到此表
- 每 15 秒发送一次心跳，更新 `last_heartbeat`
- Core 定期检查，超过 30 秒未心跳则标记为 INACTIVE

### 6. billing_records（计费记录表）

存储消息发送的计费信息。

```sql
CREATE TABLE billing_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tenant_id VARCHAR(100) NOT NULL COMMENT '租户ID',
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
    channel VARCHAR(100) NOT NULL COMMENT '渠道',
    sent_at TIMESTAMP NOT NULL COMMENT '发送时间',
    quantity INT DEFAULT 1 COMMENT '消息数量',
    unit_cost DECIMAL(10, 4) COMMENT '单价',
    total_cost DECIMAL(10, 2) COMMENT '总成本',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '货币单位',

    INDEX idx_tenant_id (tenant_id),
    INDEX idx_sent_at (sent_at),
    INDEX idx_tenant_date (tenant_id, sent_at),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费记录表';
```

**计费规则示例**：

| 渠道 | 单价（CNY） | 说明 |
|------|------------|------|
| sms | 0.05 | 每条短信 |
| email | 0.01 | 每封邮件 |
| feishu | 0.00 | 免费（企业内部） |

### 7. message_templates（消息模板表）

存储消息模板，支持多语言和多渠道。

```sql
CREATE TABLE message_templates (
    template_id VARCHAR(100) PRIMARY KEY COMMENT '模板ID',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    category VARCHAR(50) NOT NULL COMMENT '分类: verification/alert/marketing',
    contents JSON NOT NULL COMMENT '多语言内容',
    active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    version INT DEFAULT 1 COMMENT '版本号',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_category (category),
    INDEX idx_active (active),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板表';
```

**contents JSON 结构**：

```json
{
  "zh-CN": {
    "sms": {
      "text": "您的验证码是 {{code}}，有效期 {{ttl}}"
    },
    "email": {
      "subject": "验证码",
      "text": "您的验证码是 {{code}}",
      "html": "<p>您的验证码是 <strong>{{code}}</strong></p>"
    },
    "feishu": {
      "markdown": "**验证码**: {{code}}\n**有效期**: {{ttl}}"
    }
  },
  "en-US": {
    "sms": {
      "text": "Your verification code is {{code}}, valid for {{ttl}}"
    },
    "email": {
      "subject": "Verification Code",
      "text": "Your verification code is {{code}}",
      "html": "<p>Your verification code is <strong>{{code}}</strong></p>"
    }
  }
}
```

### 8. recipients（用户接收者信息表）

传统功能，存储用户的接收者信息和订阅偏好。

```sql
CREATE TABLE recipients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tenant_id VARCHAR(100) NOT NULL COMMENT '租户ID',
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',

    -- 联系方式
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(255) COMMENT '邮箱',
    wechat_openid VARCHAR(255) COMMENT '微信OpenID',
    feishu_user_id VARCHAR(255) COMMENT '飞书用户ID',

    -- 订阅状态
    subscription_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用订阅',
    subscribed_channels JSON COMMENT '订阅的渠道',

    -- 用户偏好
    preferred_channels JSON COMMENT '偏好渠道（按优先级）',
    quiet_hours_start TIME COMMENT '免打扰开始时间',
    quiet_hours_end TIME COMMENT '免打扰结束时间',

    -- 用户标签
    tags JSON COMMENT '用户标签',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_tenant_user (tenant_id, user_id),
    INDEX idx_phone (phone),
    INDEX idx_email (email),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户接收者信息表';
```

## 索引设计原则

### 1. 查询频率优先

为高频查询字段创建索引：
- `tenant_id`：几乎所有查询都需要租户隔离
- `status`：查询特定状态的消息
- `created_at`：按时间范围查询

### 2. 组合索引

对于多条件查询，创建组合索引：
- `(tenant_id, status)`：租户 + 状态查询
- `(tenant_id, sent_at)`：租户 + 时间范围查询

### 3. 覆盖索引

对于只需要索引列的查询，使用覆盖索引避免回表。

### 4. 索引维护

- 定期分析索引使用情况
- 删除未使用的索引
- 重建碎片化的索引

## 数据库优化

### 1. 分区策略

对于大表（如 `messages`、`billing_records`），可以按时间分区：

```sql
ALTER TABLE messages
PARTITION BY RANGE (UNIX_TIMESTAMP(created_at)) (
    PARTITION p202601 VALUES LESS THAN (UNIX_TIMESTAMP('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (UNIX_TIMESTAMP('2026-03-01')),
    PARTITION p202603 VALUES LESS THAN (UNIX_TIMESTAMP('2026-04-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

### 2. 冷热数据分离

- **热数据**（7 天）：保留在 MySQL
- **温数据**（30 天）：归档到 Elasticsearch（可选）
- **冷数据**（90 天+）：归档到对象存储（可选）

### 3. 读写分离

- 主库：写操作
- 从库：读操作（查询、统计）

### 4. 连接池优化

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 最大连接数
      minimum-idle: 5            # 最小空闲连接
      connection-timeout: 30000  # 连接超时 30s
      idle-timeout: 600000       # 空闲超时 10min
      max-lifetime: 1800000      # 最大生命周期 30min
```

## 数据一致性保证

### 1. 事务管理

```java
@Transactional
public void createMessage(Message message) {
    // 1. 插入消息主表
    messageRepository.save(message);

    // 2. 插入消息状态表
    MessageState state = new MessageState();
    state.setMessageId(message.getMessageId());
    state.setStatus(MessageStatus.PENDING);
    messageStateRepository.save(state);

    // 事务提交后，两张表数据一致
}
```

### 2. 唯一约束

通过唯一约束防止重复插入：

```sql
-- message_id 作为主键，天然唯一
-- (tenant_id, user_id) 组合唯一
UNIQUE KEY uk_tenant_user (tenant_id, user_id)
```

### 3. 外键约束

```sql
FOREIGN KEY (message_id) REFERENCES messages(message_id)
```

注意：高并发场景下，外键约束可能影响性能，可以考虑应用层保证一致性。

## 数据备份策略

### 1. 全量备份

每天凌晨 2 点执行全量备份：

```bash
mysqldump -u root -p messagepulse > backup_$(date +%Y%m%d).sql
```

### 2. 增量备份

启用 binlog，支持增量备份和数据恢复：

```ini
[mysqld]
log-bin=mysql-bin
binlog_format=ROW
expire_logs_days=7
```

### 3. 备份保留策略

- 全量备份：保留 30 天
- 增量备份：保留 7 天

## 与其他知识文档的关系

- **系统架构** → `02-architecture.md`：数据库在存储层的位置
- **API 设计** → `05-api-design.md`：API 如何操作数据库
- **安全设计** → `08-security.md`：API Key 表的使用
- **Channel Skills** → `09-channel-skills.md`：Skill 配置表和实例表的使用
