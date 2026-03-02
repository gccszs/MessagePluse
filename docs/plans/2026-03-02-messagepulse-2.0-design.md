# MessagePulse 2.0 - AI 时代消息基础设施设计文档

**版本**: 2.1
**日期**: 2026-03-02
**作者**: AI 协作设计

**更新记录**：
- v2.1 (2026-03-02): 新增传统功能融合设计（第十一章）
- v2.0 (2026-03-02): 初始版本，完整 AI 时代架构设计

---

## 一、项目概述

### 1.1 项目定位

MessagePulse 2.0 是面向 AI 时代的统一消息分发平台，核心价值是**解耦 AI 系统与消息渠道**。

**核心使命**：
- AI 系统侧：OpenClaw、Cursor、Claude Code 等 AI 框架只需开发一个 MessagePulse 适配器，即可接入所有下游渠道
- 消息渠道侧：短信、邮件、微信、飞书、钉钉等渠道以独立 Skill 形式运行，按需激活和部署
- 平台核心：提供统一的消息去重、链路追踪、分阶段撤回、死信队列等基础能力

### 1.2 设计哲学

```
传统模式（复杂）：
OpenClaw → 短信插件（复杂）
         → 邮件插件（复杂）
         → QQ插件（复杂）
         → 微信插件（复杂）

新方案（解耦）：
OpenClaw → MessagePulse 插件（统一接口）
         → MessagePulse → 短信/邮件/QQ/微信/飞书
```

**核心优势**：
- 🔌 **插件化**：每个渠道独立 Skill，按需激活
- 🔀 **解耦**：AI 系统无需关心具体渠道
- 📦 **可扩展**：新增渠道 = 新增 Skill
- 🎛️ **用户可控**：想接入飞书？激活飞书 Skill

### 1.3 技术栈

**核心框架**：
- Spring Boot 3.x
- Apache Kafka 3.x
- MySQL 8.x
- Redis 7.x

**可观测性**：
- Prometheus + Grafana
- SkyWalking / Zipkin
- ELK Stack

**容器化**：
- Docker + Docker Compose
- Kubernetes (可选)

---

## 二、系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│  AI 系统层                                               │
│  OpenClaw | Cursor | Claude Code | Custom AI Agents    │
│  ↓ Kafka Producer (统一消息格式)                          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  MessagePulse Core (核心平台)                            │
│  ┌─────────────┬─────────────┬─────────────┐          │
│  │ 接入层      │ 处理层      │ 存储层      │          │
│  │ REST API    │ 去重引擎    │ Kafka 3节点 │          │
│  │ Kafka Prov. │ 撤回引擎    │ Redis       │          │
│  │             │ 链路追踪    │ MySQL       │          │
│  └─────────────┴─────────────┴─────────────┘          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  Channel Skills (独立进程，按 Kafka Topic 订阅)           │
│  ┌──────────┬──────────┬──────────┬──────────┐          │
│  │ 短信Skill │ 邮件Skill │ 微信Skill │ 飞书Skill │ ...     │
│  │ Java     │ Python   │ Java     │ Python   │          │
│  └──────────┴──────────┴──────────┴──────────┘          │
└─────────────────────────────────────────────────────────┘
```

### 2.2 分层架构

#### 接入层
- REST API：消息发送、状态查询、配置管理
- Kafka Producer：接收 AI 系统消息
- API 认证：API Key / JWT Token

#### 处理层
- 路由引擎：分级路由机制
- 去重引擎：三级去重（布隆过滤器 + Redis + MySQL）
- 撤回引擎：分阶段撤回
- 一致性引擎：策略化一致性处理

#### 存储层
- Kafka 3节点集群：消息队列
- Redis：去重、缓存、限流
- MySQL：消息状态、配置、租户数据

---

## 三、核心架构设计决策

### 3.1 消息路由机制：分级路由

**设计理念**：根据消息优先级和业务场景，采用不同路由策略

#### 路由分级

```
┌─────────────────────────────────────────────────────────┐
│ 路由模式分级                                             │
├─────────────────────────────────────────────────────────┤
│ High Priority  → 显式路由 + 审批机制                    │
│   - 系统告警                                            │
│   - 金融通知                                            │
│   - 安全事件                                            │
│                                                         │
│ Normal Priority → 隐式路由 + 策略引擎                   │
│   - 业务通知                                            │
│   - 验证码                                              │
│   - 营销消息                                            │
│                                                         │
│ Low Priority   → 自动路由 + 智能决策                    │
│   - 日志报告                                            │
│   - 定时摘要                                            │
└─────────────────────────────────────────────────────────┘
```

#### 消息格式

```json
{
  "messageId": "msg_12345",
  "content": {
    "title": "告警通知",
    "body": "服务器 CPU 使用率 95%",
    "template": {
      "id": "alert_v1",
      "variables": {"cpu": "95%"}
    }
  },
  "routing": {
    "mode": "explicit",      // explicit | implicit | auto
    "channels": ["sms", "feishu"],
    "strategy": "all"        // all | any | priority_order
  },
  "priority": "high",        // high | normal | low
  "recipient": {
    "userId": "user_123",
    "phone": "13800138000",
    "email": "user@example.com",
    "feishuUserId": "ou_xxx"
  }
}
```

#### 安全检查流程

```
1. API Key 验证
   ↓ 验证通过
2. 租户渠道权限检查
   - tenant_123 是否允许使用 sms？
   - tenant_123 是否允许使用 feishu？
   ↓ 权限通过
3. Recipient 授权检查
   - 该 AI 系统是否有权限向该用户发送？
   - 用户是否订阅了该类型通知？
   ↓ 授权通过
4. 配额检查
   - 租户短信配额是否充足？
   - 是否触发限流？
   ↓ 通过
5. 内容安全检查
   - 短信敏感词过滤
   - 反垃圾邮件检查
   ↓ 通过
6. 发送消息
```

### 3.2 消息格式：动态格式生态系统

**设计理念**：消息格式不再是硬编码，而是可配置的 Schema，通过 AI Agent 降低使用门槛

#### 架构组件

```
┌─────────────────────────────────────────────────────────┐
│  Format Schema Registry (格式注册中心)                    │
│  - 飞书 Card Schema                                      │
│  - 钉钉 Markdown Schema                                 │
│  - 微信模板消息 Schema                                  │
│  - 用户自定义 Schema...                                 │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  Guide Skill (智能向导)                                  │
│  1. 理解意图：发送告警到飞书                             │
│  2. 查询格式库：飞书支持 card、markdown、text           │
│  3. 推荐方案：推荐使用 card（展示效果最佳）              │
│  4. 生成配置：自动生成消息格式配置                       │
│  5. 验证配置：检查格式是否正确                           │
└─────────────────────────────────────────────────────────┘
```

#### Format Schema 示例

```yaml
# format_schemas/feishu_card.yaml
name: "飞书卡片消息"
version: "1.0"
platform: "feishu"
content_type: "interactive/json"

schema:
  type: object
  properties:
    config:
      type: object
      properties:
        wide_screen_mode:
          type: boolean
        enable_forward:
          type: boolean
    header:
      type: object
      properties:
        title:
          type: object
          properties:
            tag:
              type: string
              enum: ["plain_text"]
            content:
              type: string
    elements:
      type: array
      items:
        oneOf:
          - $ref: "#/definitions/div_element"
          - $ref: "#/definitions/action_element"
          - $ref: "#/definitions/image_element"

example:
  config:
    wide_screen_mode: true
  header:
    title:
      tag: "plain_text"
      content: "告警通知"
  elements:
    - tag: "div"
      text:
        tag: "lark_md"
        content: "**服务器**: prod-01\n**CPU**: 95%"
```

#### 消息格式（混合模式）

```json
{
  "messageId": "msg_12345",
  "content": {
    // 模式 1：模板引用（推荐用于标准化场景）
    "template": {
      "id": "verification_code_v1",
      "variables": {
        "code": "123456",
        "ttl": "5分钟"
      }
    },

    // 模式 2：自由内容（推荐用于自定义场景）
    "title": "紧急告警",
    "body": "服务器 CPU 使用率超过 90%",

    // 多模态内容
    "formats": {
      "text": "服务器 CPU 95%",              // 短信用
      "html": "<h1>CPU 告警</h1><p>95%</p>",  // 邮件用
      "markdown": "# 告警\nCPU: 95%",         // 飞书用
      "card": {                              // 飞书卡片
        "elements": [...]
      }
    }
  },
  "mode": "template"  // template | free
}
```

### 3.3 Kafka Topic 设计：统一 Topic + 多分区

**设计理念**：保持发送简单性，通过多分区和消息键解决扩展性

#### Topic 结构

```
Topic: messagepulse.messages
Partitions: 20
Replication Factor: 3

┌─────────────────────────────────────────────────────────┐
│  Partition 0 │ Partition 1 │ ... │ Partition 19        │
├─────────────────────────────────────────────────────────┤
│  userId: u1  │  userId: u2  │     │  userId: uN       │
│  所有渠道    │  所有渠道    │     │  所有渠道          │
└─────────────────────────────────────────────────────────┘
```

#### 消息键策略

| 优先级 | 消息键 | 说明 |
|--------|--------|------|
| High Priority | channel | 确保高负载渠道独立 |
| Normal Priority | userId | 保证用户级顺序 |
| Low Priority | random | 最大化并发 |
| Multi-Tenant | tenantId | 租户隔离 |

#### 消费者组模式

```
┌─────────────────────────────────────────────────────────┐
│  Partition 0-2 │ Partition 3-5 │ Partition 6-9         │
├─────────────────────────────────────────────────────────┤
│  SMS Skill #1  │  SMS Skill #2  │  SMS Skill #3        │
│  消费者组      │  消费者组      │  消费者组            │
└─────────────────────────────────────────────────────────┘

Kafka 自动重新分配 Partition
```

### 3.4 Skill 发现与注册：配置中心 + 服务注册

#### 架构

```
┌─────────────────────────────────────────────────────────┐
│  配置中心 (MessagePulse Core 内置)                       │
│  - SkillConfig JPA 实体                                 │
│  - 静态配置（Topic、消费者组等）                        │
└─────────────────────────────────────────────────────────┘
                         ↓ HTTP
┌─────────────────────────────────────────────────────────┐
│  服务注册表 (MySQL)                                     │
│  - skill_instances 表                                    │
│  - 动态信息（实例地址、健康状态）                       │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  Channel Skills (Spring Boot)                          │
│  1. 启动时从 Core 获取配置                             │
│  2. 注册到服务注册表                                   │
│  3. 定期心跳（每 15 秒）                                │
│  4. 监听配置变更                                       │
└─────────────────────────────────────────────────────────┘
```

#### 配置实体

```java
@Entity
@Table(name = "skill_configs")
public class SkillConfig {
    private Long id;
    private String channelName;        // sms, feishu, email
    private String displayName;
    private String consumerGroup;      // messagepulse.sms
    private String topic;              // messagepulse.messages
    private String filterExpression;   // channels contains 'sms'
    private Boolean enabled;
    private Map<String, Object> channelConfig;
}
```

#### 服务注册表

```sql
CREATE TABLE skill_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id VARCHAR(255) NOT NULL,
    channel VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_id (skill_id)
);
```

### 3.5 消息确认与回执：单向回执 + 状态查询

#### 回执架构

```
┌─────────────────────────────────────────────────────────┐
│  AI 系统                                                │
│  Producer: messagepulse.messages                        │
│  Consumer: messagepulse.receipts  ← 监听回执            │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  messagepulse.messages                                  │
│  消息发送                                                │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  Channel Skills                                         │
│  发送完成后 → messagepulse.receipts (回执)              │
└─────────────────────────────────────────────────────────┘
```

#### 回执消息格式

```java
public class MessageReceipt {
    // 关联原始消息
    private String messageId;
    private String channel;
    private String recipient;

    // 发送状态
    private DeliveryStatus status;  // SENDING, SENT, DELIVERED, FAILED, REVOKED
    private String statusMessage;

    // 时间信息
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private Long durationMs;

    // 渠道特定信息
    private String externalMessageId;
    private Map<String, Object> metadata;

    // 错误信息（如果失败）
    private ErrorCode errorCode;  // RATE_LIMIT, INVALID_RECIPIENT, CHANNEL_ERROR, TIMEOUT
    private String errorMessage;
    private Boolean retryable;
}
```

#### 状态查询 API

```
GET /api/messages/{messageId}/status
Response: MessageStatus

GET /api/messages/{messageId}/receipts
Response: List<MessageReceipt>
```

### 3.6 多渠道投递：策略化一致性

#### 一致性策略

```java
public enum ConsistencyStrategy {
    /**
     * 最终一致性：接受部分成功，失败重试
     */
    EVENTUAL_CONSISTENCY,

    /**
     * 至少一个成功：只要有一个渠道成功即可
     */
    AT_LEAST_ONE,

    /**
     * 全部或全部：要么全部成功，要么全部失败
     */
    ALL_OR_NONE,

    /**
     * 优先级顺序：按优先级依次尝试，失败则停止
     */
    PRIORITY_ORDER
}
```

#### 配置示例

```yaml
consistency_strategies:
  verification_code:
    strategy: AT_LEAST_ONE  # 验证码：至少一个渠道成功即可
    priority: [sms, feishu, email]

  critical_alert:
    strategy: ALL_OR_NONE   # 关键告警：全部成功或全部失败
    channels: [sms, feishu, email]

  marketing:
    strategy: EVENTUAL_CONSISTENCY  # 营销消息：最终一致性
    retry_on_failure: true

  notification:
    strategy: PRIORITY_ORDER  # 通知：按优先级顺序
    priority: [feishu, email, sms]
```

---

## 四、P0 核心技术实现

### 4.1 安全性

#### API Key 认证

```java
@Entity
@Table(name = "api_keys")
public class ApiKey {
    private Long id;
    private String keyId;           // ak_xxxxxxxxxxxxxxxx
    private String keySecret;       // 加密存储（BCrypt）
    private String tenantId;
    private String name;
    private Set<String> scopes;     // ["sms:send", "email:send"]
    private Set<String> allowedChannels;  // ["sms", "email"]
    private RateLimitConfig rateLimit;
    private Boolean active;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
}
```

**认证流程**：
1. AI 系统在请求头携带：`Authorization: Bearer ak_xxx.keySecret`
2. ApiKeyAuthenticationFilter 验证
3. 设置认证信息到请求上下文
4. @RequireScope 注解检查权限

#### 租户隔离

```java
@Entity
@Table(name = "messages")
public class Message {
    @Id
    private String messageId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @PrePersist
    protected void onCreate() {
        // 自动设置租户 ID
        ApiKeyAuthentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            this.tenantId = auth.getTenantId();
        }
    }
}
```

### 4.2 可靠性

#### 消息状态机

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

**状态转换规则**：
```
PENDING → PROCESSING → SENT_TO_CHANNEL → DELIVERED
                  ↘ FAILED ↗
                  ↘ REVOKED
```

#### 故障恢复和重试

```java
@Configuration
@ConfigurationProperties(prefix = "messagepulse.retry")
public class RetryConfig {
    private int maxRetries = 3;
    private Duration initialBackoff = Duration.ofSeconds(1);
    private double backoffMultiplier = 2.0;
    private Duration maxBackoff = Duration.ofMinutes(1);

    private Map<String, ErrorRetryStrategy> errorStrategies;
}

// 默认错误策略
RATE_LIMIT      → 可重试，5秒后重试
TIMEOUT         → 可重试，立即重试
INVALID_RECIPIENT → 不可重试
CHANNEL_ERROR   → 可重试，10秒后重试（指数退避）
```

### 4.3 去重实现：双缓冲布隆过滤器

#### 架构

```
时间轴：  0 min    5 min    10 min
         |--------|--------|
当前窗口：   BloomFilter-A (活跃)
历史窗口：            BloomFilter-B (备用)
切换时刻：    ↑        ↑
         重建B     重建A
```

#### 配置参数

```yaml
messagepulse:
  dedup:
    bloom-filter:
      expected-insertions: 1000000    # 预期元素数（5分钟）
      false-probability: 0.0001        # 误判率 0.01%
      window-size-seconds: 300         # 时间窗口 5分钟

# 内存计算：
# 位图大小 ≈ 350 KB
# 哈希函数 ≈ 13 个
# 总内存（双缓冲）≈ 700 KB
```

#### 三级去重

```java
public boolean isDuplicate(String messageId) {
    // 第一级：布隆过滤器（快速过滤，几乎零成本）
    if (bloomFilter.mightContain(messageId)) {

        // 第二级：Redis 精确去重（高并发）
        String redisKey = "msg:dedup:" + messageId;
        if (redisTemplate.hasKey(redisKey)) {
            return true;
        }
    }

    // 添加到布隆过滤器
    bloomFilter.put(messageId);
    return false;
}

// 标记为已处理
public void markAsProcessed(String messageId) {
    bloomFilter.put(messageId);

    // 添加到 Redis（带过期时间 10 分钟）
    redisTemplate.opsForValue().set(
        "msg:dedup:" + messageId,
        "1",
        Duration.ofMinutes(10)
    );

    // 第三级：MySQL 兜底（一致性保证）
    // 通过消息状态表中的唯一约束实现
}
```

### 4.4 监控和链路追踪

#### 关键指标

```java
// 消息发送计数
Counter.builder("messagepulse.messages.sent")
    .tag("channel", channel)
    .tag("status", status)
    .register(meterRegistry);

// 消息延迟分布
Timer.builder("messagepulse.messages.delivery_time")
    .tag("channel", channel)
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(meterRegistry);

// 去重命中率
Counter.builder("messagepulse.deduplication.hit")
    .tag("level", "bloom")  // bloom, redis
    .register(meterRegistry);
```

#### Prometheus 配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        messagepulse.messages.delivery_time: true
      percentiles:
        messagepulse.messages.delivery_time: 0.5, 0.95, 0.99
      sla:
        messagepulse.messages.delivery_time: 50ms, 100ms, 500ms
```

---

## 五、P1 功能实现

### 5.1 Rate Limiting（限流）

#### 限流维度

```java
@Entity
@Table(name = "rate_limits")
public class RateLimitConfig {
    private Long id;
    private String targetType;  // API_KEY, TENANT, CHANNEL

    // API Key 级别
    private String apiKeyId;
    private int requestsPerMinute;
    private int requestsPerHour;
    private int requestsPerDay;

    // 租户级别
    private String tenantId;
    private Map<String, TenantChannelLimit> channelLimits;

    // 渠道级别
    private String channel;
    private int channelMaxQPS;
}

public class TenantChannelLimit {
    private int dailyLimit;      // 每日限额
    private int costPerMessage;  // 每条消息成本
}
```

#### 限流算法：令牌桶

```java
@Component
public class RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;

    public boolean allowRequest(String apiKeyId, String tenantId, String channel) {
        // 1. API Key 级别限流
        if (!checkApiKeyRateLimit(apiKeyId)) {
            return false;
        }

        // 2. 租户级别限流
        if (!checkTenantRateLimit(tenantId, channel)) {
            return false;
        }

        // 3. 渠道级别限流
        if (!checkChannelRateLimit(channel)) {
            return false;
        }

        return true;
    }

    private boolean checkApiKeyRateLimit(String apiKeyId) {
        String key = "ratelimit:apikey:" + apiKeyId;
        // Lua 脚本实现令牌桶
        return redisTemplate.execute(rateLimitScript, key, "60", "100");
    }
}
```

### 5.2 计费统计

#### 计费实体

```java
@Entity
@Table(name = "billing_records")
public class BillingRecord {
    private Long id;
    private String tenantId;
    private String messageId;
    private String channel;
    private LocalDateTime sentAt;

    // 计费信息
    private int quantity;        // 消息数量
    private BigDecimal unitCost; // 单价
    private BigDecimal totalCost;// 总成本

    private String currency;     // CNY, USD
}
```

#### 统计 API

```
GET /api/billing/summary?tenantId=xxx&startDate=xxx&endDate=xxx

Response:
{
  "totalCost": 1234.56,
  "totalMessages": 100000,
  "breakdownByChannel": {
    "sms": {"count": 50000, "cost": 500.00},
    "email": {"count": 40000, "cost": 200.00},
    "feishu": {"count": 10000, "cost": 534.56}
  }
}
```

### 5.3 消息模板系统

#### 模板实体

```java
@Entity
@Table(name = "message_templates")
public class MessageTemplate {
    @Id
    private String templateId;

    private String name;
    private String category;  // verification, alert, marketing

    // 多语言支持
    @Column(columnDefinition = "JSON")
    private Map<String, LocaleContent> contents;

    private Boolean active;
    private LocalDateTime createdAt;
    private Integer version;
}

public class LocaleContent {
    private String language;  // zh-CN, en-US
    private Map<String, ChannelTemplate> channels;
}

public class ChannelTemplate {
    private String subject;      // 邮件主题
    private String text;         // 纯文本
    private String html;         // HTML
    private String markdown;     // Markdown
    private Map<String, Object> card;  // 卡片消息
}
```

#### 模板渲染

```java
@Service
public class TemplateService {

    private final SpringTemplateEngine templateEngine;

    public String render(String templateId, String channel, String locale, Map<String, Object> variables) {
        MessageTemplate template = templateRepository.findById(templateId).orElseThrow();

        ChannelTemplate channelTemplate = template.getContents()
            .get(locale)
            .getChannels()
            .get(channel);

        // 使用 Thymeleaf 渲染
        Context context = new Context();
        context.setVariables(variables);

        return templateEngine.process(channelTemplate.getText(), context);
    }
}
```

### 5.4 消息生命周期管理

#### 超时处理

```java
@Scheduled(fixedRate = 60000)  // 每分钟执行
public void checkExpiredMessages() {
    List<MessageState> expiredStates = stateRepository
        .findByStatusAndExpiresAtBefore(
            MessageStatus.PROCESSING,
            LocalDateTime.now()
        );

    for (MessageState state : expiredStates) {
        // 标记为过期
        state.setStatus(MessageStatus.EXPIRED);
        stateRepository.save(state);

        // 发送告警
        alertService.sendAlert(
            state.getMessageId(),
            "Message expired during processing"
        );
    }
}
```

---

## 六、数据库设计

### 6.1 核心表结构

#### 消息表 (messages)

```sql
CREATE TABLE messages (
    message_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    content JSON NOT NULL,
    routing JSON NOT NULL,
    priority VARCHAR(20) NOT NULL,
    recipient JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    expires_at TIMESTAMP,

    INDEX idx_tenant_id (tenant_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_tenant_status (tenant_id, status)
);
```

#### 消息状态表 (message_states)

```sql
CREATE TABLE message_states (
    message_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    channel_statuses JSON,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    last_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    expires_at TIMESTAMP,

    FOREIGN KEY (message_id) REFERENCES messages(message_id)
);
```

#### API Keys 表 (api_keys)

```sql
CREATE TABLE api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_id VARCHAR(64) UNIQUE NOT NULL,
    key_secret VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    scopes JSON NOT NULL,
    allowed_channels JSON NOT NULL,
    rate_limit JSON,
    active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    created_by VARCHAR(100),

    INDEX idx_tenant_id (tenant_id),
    INDEX idx_key_id (key_id)
);
```

#### Skills 配置表 (skill_configs)

```sql
CREATE TABLE skill_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_name VARCHAR(100) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    filter_expression TEXT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    channel_config JSON,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
```

#### Skills 实例表 (skill_instances)

```sql
CREATE TABLE skill_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id VARCHAR(255) UNIQUE NOT NULL,
    channel VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL,

    INDEX idx_channel (channel),
    INDEX idx_last_heartbeat (last_heartbeat)
);
```

#### 计费记录表 (billing_records)

```sql
CREATE TABLE billing_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    channel VARCHAR(100) NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    quantity INT DEFAULT 1,
    unit_cost DECIMAL(10, 4),
    total_cost DECIMAL(10, 2),
    currency VARCHAR(10) DEFAULT 'CNY',

    INDEX idx_tenant_id (tenant_id),
    INDEX idx_sent_at (sent_at),
    INDEX idx_tenant_date (tenant_id, sent_at)
);
```

#### 消息模板表 (message_templates)

```sql
CREATE TABLE message_templates (
    template_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    contents JSON NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    version INT DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,

    INDEX idx_category (category),
    INDEX idx_active (active)
);
```

---

## 七、API 设计

### 7.1 消息发送 API

```
POST /api/v1/messages
Authorization: Bearer ak_xxx.keySecret

Request:
{
  "content": {
    "template": {
      "id": "verification_code_v1",
      "variables": {"code": "123456"}
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

Response (201 Created):
{
  "messageId": "msg_20260302_123456",
  "status": "PENDING",
  "createdAt": "2026-03-02T10:30:00Z"
}
```

### 7.2 消息状态查询 API

```
GET /api/v1/messages/{messageId}/status

Response:
{
  "messageId": "msg_20260302_123456",
  "status": "DELIVERED",
  "overallStatus": "PARTIAL_SUCCESS",
  "channelStatuses": {
    "sms": {
      "status": "DELIVERED",
      "sentAt": "2026-03-02T10:30:01Z",
      "deliveredAt": "2026-03-02T10:30:03Z",
      "durationMs": 2000
    },
    "feishu": {
      "status": "FAILED",
      "errorCode": "RATE_LIMIT",
      "errorMessage": "频率限制",
      "retryable": true
    }
  }
}
```

### 7.3 API Key 管理 API

```
POST /api/v1/api-keys

Request:
{
  "name": "Production Key",
  "tenantId": "tenant_123",
  "scopes": ["message:send", "message:query"],
  "allowedChannels": ["sms", "email"],
  "rateLimit": {
    "requestsPerMinute": 100,
    "requestsPerDay": 10000
  }
}

Response:
{
  "keyId": "ak_abc123...",
  "keySecret": "xyz789...",  // 仅此一次显示
  "tenantId": "tenant_123",
  "name": "Production Key"
}
```

### 7.4 渠道 Skill 管理 API

```
GET /api/v1/skills

Response:
{
  "skills": [
    {
      "channel": "sms",
      "displayName": "短信",
      "enabled": true,
      "instances": [
        {
          "skillId": "sms-skill-1",
          "address": "192.168.1.10",
          "port": 8081,
          "status": "ACTIVE"
        }
      ]
    },
    {
      "channel": "feishu",
      "displayName": "飞书",
      "enabled": true,
      "instances": [...]
    }
  ]
}

POST /api/v1/skills/{channel}/enable
POST /api/v1/skills/{channel}/disable
```

---

## 八、部署方案

### 8.1 Docker Compose 部署

```yaml
version: '3.8'

services:
  # MessagePulse Core
  messagepulse-core:
    image: messagepulse/core:2.0.0
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/messagepulse
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - SPRING_REDIS_HOST=redis
    depends_on:
      - mysql
      - kafka
      - redis

  # Kafka
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
    depends_on:
      - zookeeper

  # MySQL
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: messagepulse
    volumes:
      - mysql_data:/var/lib/mysql

  # Redis
  redis:
    image: redis:7-alpine
    volumes:
      - redis_data:/data

  # Channel Skills
  sms-skill:
    image: messagepulse/sms-skill:2.0.0
    environment:
      - MESSAGEPULSE_CHANNEL_NAME=sms
      - MESSAGEPULSE_CORE_BASE_URL=http://messagepulse-core:8080
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    depends_on:
      - messagepulse-core
      - kafka

  feishu-skill:
    image: messagepulse/feishu-skill:2.0.0
    environment:
      - MESSAGEPULSE_CHANNEL_NAME=feishu
      - MESSAGEPULSE_CORE_BASE_URL=http://messagepulse-core:8080
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    depends_on:
      - messagepulse-core
      - kafka

volumes:
  mysql_data:
  redis_data:
```

### 8.2 性能指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 接口层 QPS | 12000+ | 峰值吞吐 |
| 接口延迟 P99 | < 20ms | 接口响应时间 |
| 消息重复率 | < 0.01% | 去重后 |
| 消息成功率 | > 99% | 所有渠道综合 |
| 内存占用 | ~8GB | 3节点 Kafka + 其他组件 |

---

## 九、实施计划

### 9.1 开发阶段

| 阶段 | 内容 | 里程碑 |
|------|------|--------|
| Phase 1 | 核心框架搭建 | Spring Boot 项目初始化 |
| Phase 2 | API 认证授权 | API Key 系统可用 |
| Phase 3 | Kafka 集成 | 消息发送流程打通 |
| Phase 4 | 去重引擎 | 三级去重实现 |
| Phase 5 | Channel Skills | 短信、邮件 Skill 完成 |
| Phase 6 | 监控系统 | Prometheus + Grafana 可用 |
| Phase 7 | 压力测试 | 性能指标达标 |
| Phase 8 | 文档完善 | API 文档、部署文档 |

### 9.2 测试策略

**单元测试**：
- JUnit 5 + Mockito
- 测试覆盖率 > 80%

**集成测试**：
- Testcontainers (Kafka, MySQL, Redis)
- API 集成测试

**压力测试**：
- JMeter 脚本
- 场景：不同并发级别 (1K, 5K, 10K, 15K QPS)

---

## 十一、传统功能融合设计

### 11.1 设计目标

MessagePulse 2.0 在面向 AI 时代的同时，必须完整保留传统消息推送系统的核心功能，确保：

1. **向后兼容**：支持传统业务系统（Java、.NET、PHP 等）接入
2. **功能完整**：保留定时任务、批量发送、用户管理等传统功能
3. **架构统一**：传统功能与 AI 时代特性在统一架构下实现
4. **渐进演进**：支持从传统系统平滑迁移到 AI 时代架构

### 11.2 传统功能架构

```
┌─────────────────────────────────────────────────────────┐
│  接入层（API 优先）                                      │
├─────────────────────────────────────────────────────────┤
│  • REST API（统一接口）                                  │
│    - AI 系统接入（OpenClaw、Cursor、Claude Code）       │
│    - 传统业务系统接入（Java、.NET、PHP）                │
│    - 多语言 SDK（Java、.NET、PHP、Python）             │
│                                                         │
│  • Web 管理界面（分阶段实现）                            │
│    Phase A（最小化）：                                  │
│    - 发送记录查询                                        │
│    - 消息状态查看                                        │
│    - 基础统计概览                                        │
│    - API Key 管理                                       │
│    - Channel Skills 状态监控                            │
│                                                         │
│    Phase B（标准）：                                    │
│    - 消息模板管理                                        │
│    - 用户订阅管理                                        │
│    - 渠道配置管理                                        │
│    - 实时监控大屏                                        │
│                                                         │
│    Phase C（完整，愿景）：                              │
│    - 用户偏好设置                                        │
│    - 定时任务管理                                        │
│    - 批量发送界面                                        │
│    - 账单和配额管理                                      │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  任务调度层（XXL-Job）                                   │
├─────────────────────────────────────────────────────────┤
│  • 定时任务：Cron 表达式调度                            │
│    - 每天早上 9 点发送报表                              │
│    - 每小时发送统计摘要                                  │
│                                                         │
│  • 批量任务：大容量消息处理                             │
│    - 向 1000 个用户群发通知                             │
│    - 文件导入批量发送                                    │
│                                                         │
│  • 延迟任务：定时发送                                   │
│    - 5 分钟后发送提醒                                    │
│    - 延迟到指定时间发送                                  │
│                                                         │
│  • 任务管理：                                           │
│    - 任务依赖                                            │
│    - 失败重试                                            │
│    - 执行日志                                            │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  用户管理层（最小化 + 外部集成）                          │
├─────────────────────────────────────────────────────────┤
│  • 最小化用户管理：                                      │
│    - 用户接收者信息（userId、phone、email 等）         │
│    - 用户订阅状态（订阅/退订）                           │
│    - 用户偏好设置（接收渠道：短信/邮件/飞书）           │
│    - 用户标签分组                                        │
│                                                         │
│  • 外部用户系统集成：                                    │
│    - API 对接企业现有用户系统                           │
│    - 用户信息实时查询                                    │
│    - 订阅/偏好数据由外部系统管理                        │
│    - 支持 SSO 单点登录                                  │
│                                                         │
│  • 用户数据存储：                                       │
│    - 核心用户信息：MySQL                                │
│    - 查询索引：Redis 缓存                                │
│    - 外部系统：API 实时查询                              │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  监控与统计层                                           │
├─────────────────────────────────────────────────────────┤
│  • Prometheus + Grafana（主要监控）：                    │
│    - 实时 QPS 统计                                      │
│    - 消息成功率趋势                                      │
│    - 接口延迟分布（P50/P95/P99）                         │
│    - 错误率统计                                          │
│    - 系统资源监控                                        │
│                                                         │
│  • Flink（可选组件，学习演示）：                         │
│    - Kafka 消息流实时统计                                │
│    - 链路追踪数据实时聚合                                │
│    - 租户级别实时统计                                    │
│    - 状态展示延迟 < 3 秒                                 │
│    - Docker Compose 配置中默认禁用                      │
│                                                         │
│  • SkyWalking（链路追踪）：                              │
│    - 全链路耗时分析                                      │
│    - 服务依赖拓扑                                        │
│    - 异常追踪                                            │
└─────────────────────────────────────────────────────────┘
```

### 11.3 传统系统接入方案

#### 11.3.1 多语言 SDK

为传统业务系统提供官方 SDK，简化接入成本：

**Java SDK**
```java
// Maven 依赖
<dependency>
    <groupId>com.messagepulse</groupId>
    <artifactId>messagepulse-client-java</artifactId>
    <version>2.0.0</version>
</dependency>

// 使用示例
MessagePulseClient client = new MessagePulseClient.Builder()
    .apiKeyId("ak_xxx")
    .apiKeySecret("sk_xxx")
    .baseUrl("http://messagepulse.example.com")
    .build();

Message message = Message.builder()
    .content("您的验证码是 123456")
    .recipient(Recipient.builder()
        .phone("13800138000")
        .build())
    .channels(Arrays.asList("sms", "feishu"))
    .build();

SendMessageResponse response = client.sendMessage(message);
```

**.NET SDK**
```csharp
// NuGet 包
Install-Package MessagePulse.Client

// 使用示例
var client = new MessagePulseClient(new MessagePulseOptions {
    ApiKeyId = "ak_xxx",
    ApiKeySecret = "sk_xxx",
    BaseUrl = "http://messagepulse.example.com"
});

var message = new Message {
    Content = "您的验证码是 123456",
    Recipient = new Recipient {
        Phone = "13800138000"
    },
    Channels = new List<string> { "sms", "feishu" }
};

var response = await client.SendMessageAsync(message);
```

**PHP SDK**
```php
// Composer
composer require messagepulse/client-php

// 使用示例
use MessagePulse\Client;
use MessagePulse\Message;

$client = new MessagePulse\Client([
    'api_key_id' => 'ak_xxx',
    'api_key_secret' => 'sk_xxx',
    'base_url' => 'http://messagepulse.example.com'
]);

$message = new Message([
    'content' => '您的验证码是 123456',
    'recipient' => [
        'phone' => '13800138000'
    ],
    'channels' => ['sms', 'feishu']
]);

$response = $client->sendMessage($message);
```

#### 11.3.2 接入文档

为每种语言提供详细的接入文档：
- 快速开始指南
- 完整 API 参考
- 示例代码
- 最佳实践
- 常见问题 FAQ

### 11.4 XXL-Job 任务调度集成

#### 11.4.1 XXL-Job 架构

```
┌─────────────────────────────────────────────────────────┐
│  XXL-Job 调度中心                                        │
│  - 任务配置管理                                          │
│  - 任务调度执行                                          │
│  - 任务监控告警                                          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  MessagePulse 执行器                                     │
│  - 接收调度任务                                          │
│  - 执行发送逻辑                                          │
│  - 返回执行结果                                          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  Kafka                                                   │
│  messagepulse.messages Topic                             │
└─────────────────────────────────────────────────────────┘
```

#### 11.4.2 任务类型

**定时任务**
```java
@XxlJob("dailyReportHandler")
public void dailyReportHandler() {
    // 查询需要发送日报的用户
    List<User> users = userService.getUsersSubscribedTo("daily_report");

    for (User user : users) {
        // 构建消息
        Message message = Message.builder()
            .template(new Template("daily_report_v1"))
            .variable("data", reportService.generateReport(user))
            .recipient(user.getRecipient())
            .channels(Arrays.asList("email"))
            .build();

        // 发送消息
        messageService.sendMessage(message);
    }
}
```

**批量任务**
```java
@XxlJob("batchSendHandler")
public void batchSendHandler() {
    String taskId = XxlJobHelper.getJobParam();

    // 获取批量任务配置
    BatchTask batchTask = batchTaskService.getTask(taskId);

    // 分批处理，避免一次性加载过多数据
    int pageSize = 1000;
    int pageNum = 1;

    while (true) {
        List<User> users = userService.getUsersByBatch(
            batchTask.getBatchId(),
            pageNum,
            pageSize
        );

        if (users.isEmpty()) {
            break;
        }

        // 批量发送消息
        for (User user : users) {
            Message message = buildMessage(batchTask, user);
            messageService.sendMessage(message);
        }

        pageNum++;
    }
}
```

**延迟任务**
```java
@XxlJob("delayedSendHandler")
public void delayedSendHandler() {
    String messageId = XxlJobHelper.getJobParam();

    // 从数据库查询延迟任务
    DelayedTask task = delayedTaskService.getTask(messageId);

    // 检查是否需要执行
    if (task.getScheduledAt().isBefore(LocalDateTime.now())) {
        // 发送消息
        messageService.sendMessage(task.getMessage());

        // 标记任务已完成
        task.setStatus(DelayedTask.Status.COMPLETED);
        delayedTaskService.save(task);
    }
}
```

#### 11.4.3 Docker Compose 配置

```yaml
# docker-compose.yml 新增 XXL-Job 服务
xxl-job-admin:
  image: xuxueli/xxl-job-admin:2.4.0
  ports:
    - "8080:8080"
  environment:
    - PARAMS="--spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?useSSL=false --spring.datasource.username=root --spring.datasource.password=password"
  depends_on:
    - mysql

xxl-job-executor:
  image: messagepulse/core:2.0.0
  environment:
    - XXL_JOB_ADMIN_ADDRESSES=http://xxl-job-admin:8080/xxl-job-admin
    - XXL_JOB_EXECUTOR_PORT=9999
  depends_on:
    - xxl-job-admin
```

### 11.5 用户管理设计

#### 11.5.1 用户数据模型

```sql
-- 用户接收者信息表
CREATE TABLE recipients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,

    -- 联系方式
    phone VARCHAR(20),
    email VARCHAR(255),
    wechat_openid VARCHAR(255),
    feishu_user_id VARCHAR(255),

    -- 订阅状态
    subscription_enabled BOOLEAN DEFAULT TRUE,
    subscribed_channels JSON,  -- ["sms", "email", "feishu"]

    -- 用户偏好
    preferred_channels JSON,  -- 按优先级排序
    quiet_hours_start TIME,   -- 免打扰开始时间
    quiet_hours_end TIME,     -- 免打扰结束时间

    -- 用户标签
    tags JSON,  -- ["VIP", "active", "churned"]

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    UNIQUE KEY uk_tenant_user (tenant_id, user_id),
    INDEX idx_phone (phone),
    INDEX idx_email (email)
);
```

#### 11.5.2 外部用户系统集成

```java
@Service
public class ExternalUserIntegrationService {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, User> userCache;

    /**
     * 从外部系统查询用户信息
     */
    public User getUserFromExternal(String tenantId, String userId) {
        // 1. 先查缓存
        String cacheKey = String.format("user:%s:%s", tenantId, userId);
        User cached = userCache.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 调用外部系统 API
        String externalUrl = getExternalUserUrl(tenantId);
        User user = restTemplate.getForObject(
            externalUrl + "/api/users/" + userId,
            User.class
        );

        // 3. 存入缓存
        userCache.opsForValue().set(cacheKey, user, Duration.ofMinutes(5));

        return user;
    }

    /**
     * 同步用户订阅状态到外部系统
     */
    public void syncSubscriptionStatus(String tenantId, String userId, SubscriptionStatus status) {
        String externalUrl = getExternalUserUrl(tenantId);
        restTemplate.postForObject(
            externalUrl + "/api/users/" + userId + "/subscription",
            status,
            Void.class
        );
    }
}
```

#### 11.5.3 用户订阅管理

```java
@RestController
@RequestMapping("/api/recipients")
public class RecipientController {

    /**
     * 用户订阅渠道
     */
    @PostMapping("/{userId}/subscribe")
    public ResponseEntity<Void> subscribe(
            @PathVariable String userId,
            @RequestBody SubscribeRequest request) {

        Recipient recipient = recipientService.findByUserId(userId);

        // 添加订阅渠道
        request.getChannels().forEach(channel -> {
            recipient.getSubscribedChannels().add(channel);
        });

        recipientService.save(recipient);

        // 同步到外部系统
        if (recipient.isExternal()) {
            externalUserService.syncSubscriptionStatus(
                recipient.getTenantId(),
                userId,
                recipient.getSubscriptionStatus()
            );
        }

        return ResponseEntity.ok().build();
    }

    /**
     * 用户退订渠道
     */
    @PostMapping("/{userId}/unsubscribe")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable String userId,
            @RequestBody UnsubscribeRequest request) {

        Recipient recipient = recipientService.findByUserId(userId);

        // 移除订阅渠道
        request.getChannels().forEach(channel -> {
            recipient.getSubscribedChannels().remove(channel);
        });

        recipientService.save(recipient);

        return ResponseEntity.ok().build();
    }
}
```

### 11.6 Flink 实时统计（可选组件）

#### 11.6.1 Flink 在架构中的定位

```
┌─────────────────────────────────────────────────────────┐
│  Kafka Topics                                            │
│  - messagepulse.messages                                │
│  - messagepulse.receipts                                │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  Flink Streaming（可选）                                 │
│  - 实时 QPS 统计                                        │
│  - 消息成功率趋势                                       │
│  - 租户级别实时统计                                     │
│  - 异常检测                                             │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  实时统计结果存储                                        │
│  - Redis（实时数据，1 小时）                             │
│  - MySQL（聚合数据，24 小时）                            │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  Web 界面展示                                            │
│  - 实时 QPS 监控                                         │
│  - 成功率趋势图                                         │
│  - 租户使用统计                                         │
└─────────────────────────────────────────────────────────┘
```

#### 11.6.2 Flink Job 示例

```java
public class MessageStatisticsJob {

    public static void main(String[] args) throws Exception {
        // 创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 创建 Kafka Source
        FlinkKafkaConsumer<Message> kafkaSource = new FlinkKafkaConsumer<>(
            "messagepulse.messages",
            new MessageDeserializationSchema(),
            getKafkaProperties()
        );

        DataStream<Message> messageStream = env.addSource(kafkaSource);

        // 实时 QPS 统计（1秒窗口）
        messageStream
            .keyBy(Message::getTenantId)
            .window(TumblingProcessingTimeWindows.of(Time.seconds(1)))
            .aggregate(new QPSAggregator())
            .addSink(new RedisSink());

        // 消息成功率统计（1分钟窗口）
        messageStream
            .keyBy(Message::getTenantId)
            .window(TumblingProcessingTimeWindows.of(Time.minutes(1)))
            .aggregate(new SuccessRateAggregator())
            .addSink(new MySQLSink());

        // 执行
        env.execute("MessagePulse Statistics");
    }
}
```

#### 11.6.3 Docker Compose 配置

```yaml
# Flink（可选，默认注释掉）
flink-jobmanager:
  image: flink:1.18
  ports:
    - "8081:8081"
  environment:
    - FLINK_PROPERTIES=jobmanager.rpc.address:flink-jobmanager
  command: jobmanager
  # 取消注释以启用
  # depends_on:
  #   - kafka

flink-taskmanager:
  image: flink:1.18
  environment:
    - FLINK_PROPERTIES=jobmanager.rpc.address:flink-jobmanager
  command: taskmanager
  # 取消注释以启用
  # depends_on:
  #   - flink-jobmanager
```

### 11.7 消息查询设计

#### 11.7.1 分层查询方案

```
┌─────────────────────────────────────────────────────────┐
│  查询接口层                                              │
├─────────────────────────────────────────────────────────┤
│  • Web 界面：基础查询                                    │
│    - 按消息 ID 查询                                      │
│    - 按手机号/邮箱查询（最近 7 天）                      │
│    - 按时间范围查询                                      │
│                                                         │
│  • REST API：完整查询能力                                │
│    - 支持多条件组合查询                                  │
│    - 支持分页和排序                                     │
│    - 支持聚合统计                                        │
│                                                         │
│  • 运营查询：高级功能                                    │
│    - 自定义 SQL 查询（安全限制）                        │
│    - 数据导出（Excel、CSV）                              │
│    - BI 工具对接                                        │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  数据存储层（冷热分离）                                  │
├─────────────────────────────────────────────────────────┤
│  • 热数据（7 天）：MySQL                                │
│    - 频繁查询的数据                                     │
│    - 支持复杂查询                                       │
│                                                         │
│  • 温数据（30 天）：Elasticsearch（可选）                │
│    - 全文搜索                                           │
│    - 聚合统计                                           │
│                                                         │
│  • 冷数据（90 天+）：对象存储（可选）                    │
│    - 归档存储                                           │
│    - 成本优化                                           │
└─────────────────────────────────────────────────────────┘
```

#### 11.7.2 查询 API 设计

```
GET /api/v1/messages/query

Query Parameters:
  - messageId: 消息 ID（精确查询）
  - recipient: 接收者（手机号/邮箱）
  - status: 消息状态（PENDING/PROCESSING/DELIVERED/FAILED）
  - channel: 渠道（sms/email/feishu）
  - startDate: 开始时间
  - endDate: 结束时间
  - tenantId: 租户 ID
  - page: 页码（默认 1）
  - size: 每页大小（默认 20，最大 100）
  - sort: 排序字段（createdAt/sentAt）
  - order: 排序方向（asc/desc）

Response:
{
  "total": 1000,
  "page": 1,
  "size": 20,
  "messages": [
    {
      "messageId": "msg_xxx",
      "status": "DELIVERED",
      "content": "...",
      "recipient": {...},
      "channels": ["sms", "email"],
      "createdAt": "2026-03-02T10:00:00Z",
      "deliveredAt": "2026-03-02T10:00:03Z",
      "durationMs": 3000
    }
  ]
}
```

### 11.8 传统功能保留清单

| 传统功能 | 实现方式 | 优先级 | 说明 |
|---------|---------|--------|------|
| ✅ 双缓冲布隆过滤器去重 | Guava BloomFilter | P0 | 5 分钟窗口，0.01% 误判率 |
| ✅ 分阶段消息撤回 | 消息状态机 + Redis | P0 | 根据消息状态采取不同策略 |
| ✅ 性能指标（12000 QPS） | Kafka 异步解耦 | P0 | 接口层 QPS，P99 < 20ms |
| ✅ 死信队列 DLQ | Kafka DLQ Topic | P0 | 自动重试 + 人工介入 |
| ✅ 多语言 SDK | Java/.NET/PHP/Python | P1 | 简化传统系统接入 |
| ✅ XXL-Job 定时任务 | XXL-Job 集成 | P1 | 定时/批量/延迟发送 |
| ✅ Web 管理界面 | Spring Boot + Vue.js | P1 | 分阶段实现（A/B/C） |
| ✅ 用户管理 | MySQL + Redis | P1 | 最小化管理 + 外部集成 |
| ✅ Flink 实时统计 | Flink Streaming | P2 | 可选组件，学习演示 |
| ✅ 消息查询 | MySQL + Elasticsearch | P1 | 冷热分离，分层次查询 |

### 11.9 向后兼容性保证

#### 11.9.1 API 版本管理

```
/v1/api/* - 当前稳定版本
/v2/api/* - 新功能版本（AI 时代特性）
```

#### 11.9.2 配置兼容

```yaml
# 保留传统配置项
messagepulse:
  # 传统功能配置
  traditional:
    xxl-job:
      enabled: true
      admin-addresses: http://localhost:8080/xxl-job-admin

    user-management:
      mode: minimal  # minimal | external
      external-api-url: http://external-system/api

    flink:
      enabled: false  # 默认禁用
```

#### 11.9.3 渐进式迁移

```
阶段 1：传统系统使用 REST API + SDK
    ↓
阶段 2：接入 XXL-Job，支持定时/批量任务
    ↓
阶段 3：启用 Web 管理界面，提升运营效率
    ↓
阶段 4：集成 AI 系统（OpenClaw 等）
    ↓
阶段 5：启用 Guide Skill，支持动态格式
    ↓
阶段 6：完整 AI 时代架构
```

---

## 十二、总结

MessagePulse 2.0 通过以下设计实现了 AI 时代消息基础设施的目标：

1. **解耦设计**：AI 系统与消息渠道完全解耦
2. **插件化架构**：Channel Skills 独立部署、按需激活
3. **动态扩展**：Guide Skill 辅助接入新平台
4. **安全可靠**：API Key 认证、租户隔离、消息状态机
5. **高性能**：异步解耦、三级去重、多分区 Kafka
6. **可观测**：完整的监控和链路追踪

**核心价值主张**：
> 一次集成，多渠道通达；智能向导，新平台即插即用。
