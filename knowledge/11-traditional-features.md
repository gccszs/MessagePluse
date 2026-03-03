# 传统功能融合

## 设计目标

MessagePulse 2.0 在面向 AI 时代的同时，必须完整保留传统消息推送系统的核心功能，确保：

1. **向后兼容**：支持传统业务系统（Java、.NET、PHP 等）接入
2. **功能完整**：保留定时任务、批量发送、用户管理等传统功能
3. **架构统一**：传统功能与 AI 时代特性在统一架构下实现
4. **渐进演进**：支持从传统系统平滑迁移到 AI 时代架构

## 传统功能架构

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
```

## 多语言 SDK

### Java SDK

**Maven 依赖**：

```xml
<dependency>
    <groupId>com.messagepulse</groupId>
    <artifactId>messagepulse-client-java</artifactId>
    <version>2.0.0</version>
</dependency>
```

**使用示例**：

```java
// 初始化客户端
MessagePulseClient client = new MessagePulseClient.Builder()
    .apiKeyId("ak_xxx")
    .apiKeySecret("sk_xxx")
    .baseUrl("http://messagepulse.example.com")
    .build();

// 发送消息
Message message = Message.builder()
    .content("您的验证码是 123456")
    .recipient(Recipient.builder()
        .phone("13800138000")
        .build())
    .channels(Arrays.asList("sms", "feishu"))
    .build();

SendMessageResponse response = client.sendMessage(message);
System.out.println("Message ID: " + response.getMessageId());

// 查询状态
MessageStatus status = client.getMessageStatus(response.getMessageId());
System.out.println("Status: " + status.getStatus());
```

### .NET SDK

**NuGet 包**：

```bash
Install-Package MessagePulse.Client
```

**使用示例**：

```csharp
// 初始化客户端
var client = new MessagePulseClient(new MessagePulseOptions {
    ApiKeyId = "ak_xxx",
    ApiKeySecret = "sk_xxx",
    BaseUrl = "http://messagepulse.example.com"
});

// 发送消息
var message = new Message {
    Content = "您的验证码是 123456",
    Recipient = new Recipient {
        Phone = "13800138000"
    },
    Channels = new List<string> { "sms", "feishu" }
};

var response = await client.SendMessageAsync(message);
Console.WriteLine($"Message ID: {response.MessageId}");

// 查询状态
var status = await client.GetMessageStatusAsync(response.MessageId);
Console.WriteLine($"Status: {status.Status}");
```

### PHP SDK

**Composer**：

```bash
composer require messagepulse/client-php
```

**使用示例**：

```php
<?php
use MessagePulse\Client;
use MessagePulse\Message;

// 初始化客户端
$client = new Client([
    'api_key_id' => 'ak_xxx',
    'api_key_secret' => 'sk_xxx',
    'base_url' => 'http://messagepulse.example.com'
]);

// 发送消息
$message = new Message([
    'content' => '您的验证码是 123456',
    'recipient' => [
        'phone' => '13800138000'
    ],
    'channels' => ['sms', 'feishu']
]);

$response = $client->sendMessage($message);
echo "Message ID: " . $response->messageId . "\n";

// 查询状态
$status = $client->getMessageStatus($response->messageId);
echo "Status: " . $status->status . "\n";
```

### Python SDK

**pip 安装**：

```bash
pip install messagepulse-client
```

**使用示例**：

```python
from messagepulse import MessagePulseClient, Message, Recipient

# 初始化客户端
client = MessagePulseClient(
    api_key_id='ak_xxx',
    api_key_secret='sk_xxx',
    base_url='http://messagepulse.example.com'
)

# 发送消息
message = Message(
    content='您的验证码是 123456',
    recipient=Recipient(phone='13800138000'),
    channels=['sms', 'feishu']
)

response = client.send_message(message)
print(f"Message ID: {response.message_id}")

# 查询状态
status = client.get_message_status(response.message_id)
print(f"Status: {status.status}")
```

## XXL-Job 任务调度集成

### 架构

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

### 定时任务示例

```java
@Component
public class ScheduledMessageJobs {

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

        XxlJobHelper.log("Daily report sent to {} users", users.size());
    }
}
```

### 批量任务示例

```java
@XxlJob("batchSendHandler")
public void batchSendHandler() {
    String taskId = XxlJobHelper.getJobParam();

    // 获取批量任务配置
    BatchTask batchTask = batchTaskService.getTask(taskId);

    // 分批处理，避免一次性加载过多数据
    int pageSize = 1000;
    int pageNum = 1;
    int totalSent = 0;

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
            totalSent++;
        }

        XxlJobHelper.log("Batch {} sent: {} messages", pageNum, users.size());
        pageNum++;
    }

    XxlJobHelper.log("Batch task completed: {} messages sent", totalSent);
}
```

### 延迟任务示例

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

        XxlJobHelper.log("Delayed message sent: {}", messageId);
    }
}
```

### Docker Compose 配置

```yaml
xxl-job-admin:
  image: xuxueli/xxl-job-admin:2.4.0
  ports:
    - "8080:8080"
  environment:
    - PARAMS="--spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?useSSL=false --spring.datasource.username=root --spring.datasource.password=password"
  depends_on:
    - mysql
```

## 用户管理

### 数据模型

```sql
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

### 外部用户系统集成

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
    public void syncSubscriptionStatus(
        String tenantId,
        String userId,
        SubscriptionStatus status
    ) {
        String externalUrl = getExternalUserUrl(tenantId);
        restTemplate.postForObject(
            externalUrl + "/api/users/" + userId + "/subscription",
            status,
            Void.class
        );
    }
}
```

### 用户订阅管理 API

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
        @RequestBody SubscribeRequest request
    ) {
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
        @RequestBody UnsubscribeRequest request
    ) {
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

## Flink 实时统计（可选组件）

### 架构定位

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
```

### Flink Job 示例

```java
public class MessageStatisticsJob {

    public static void main(String[] args) throws Exception {
        // 创建执行环境
        StreamExecutionEnvironment env =
            StreamExecutionEnvironment.getExecutionEnvironment();

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

### Docker Compose 配置

```yaml
# Flink（可选，默认注释掉）
# flink-jobmanager:
#   image: flink:1.18
#   ports:
#     - "8081:8081"
#   environment:
#     - FLINK_PROPERTIES=jobmanager.rpc.address:flink-jobmanager
#   command: jobmanager

# flink-taskmanager:
#   image: flink:1.18
#   environment:
#     - FLINK_PROPERTIES=jobmanager.rpc.address:flink-jobmanager
#   command: taskmanager
#   depends_on:
#     - flink-jobmanager
```

## 传统功能保留清单

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

## 渐进式迁移路径

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

## 与其他知识文档的关系

- **项目概述** → `01-project-overview.md`：传统功能在项目定位中的作用
- **系统架构** → `02-architecture.md`：传统功能在架构中的位置
- **API 设计** → `05-api-design.md`：SDK 调用的 API
- **数据库设计** → `04-database-design.md`：recipients 表设计
