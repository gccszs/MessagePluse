# Kafka 设计

## Kafka 在架构中的角色

Kafka 是 MessagePulse 2.0 的核心消息队列，承担以下职责：

1. **异步解耦**：接入层与处理层解耦，处理层与 Skill 层解耦
2. **消息持久化**：消息可重放，支持故障恢复
3. **流量削峰**：应对突发流量
4. **并行处理**：通过分区机制实现并发消费

## Topic 设计

### Topic 总览

```
messagepulse.messages    # 主消息 Topic（统一入口）
messagepulse.receipts    # 回执 Topic
messagepulse.dlq         # 死信队列 Topic
```

### 1. messagepulse.messages（主消息 Topic）

**设计理念**：统一 Topic + 多分区，通过消息键实现路由。

**配置参数**：

```yaml
Topic: messagepulse.messages
Partitions: 20
Replication Factor: 3
Retention: 7 days (604800000 ms)
Compression: lz4
Min In-Sync Replicas: 2
```

**分区策略**：

```
┌─────────────────────────────────────────────────────────┐
│  Partition 0 │ Partition 1 │ ... │ Partition 19        │
├─────────────────────────────────────────────────────────┤
│  userId: u1  │  userId: u2  │     │  userId: uN       │
│  所有渠道    │  所有渠道    │     │  所有渠道          │
└─────────────────────────────────────────────────────────┘
```

**消息键（Key）策略**：

根据消息优先级选择不同的消息键：

| 优先级 | 消息键 | 说明 | 目的 |
|--------|--------|------|------|
| High Priority | `channel` | 按渠道分区 | 确保高负载渠道独立处理 |
| Normal Priority | `userId` | 按用户分区 | 保证用户级消息顺序 |
| Low Priority | `random` | 随机分区 | 最大化并发，无顺序要求 |
| Multi-Tenant | `tenantId` | 按租户分区 | 租户隔离 |

**生产者代码示例**：

```java
@Service
public class MessageProducer {

    private final KafkaTemplate<String, Message> kafkaTemplate;

    public void sendMessage(Message message) {
        // 根据优先级选择消息键
        String key = determineMessageKey(message);

        ProducerRecord<String, Message> record = new ProducerRecord<>(
            "messagepulse.messages",
            null,  // partition 由 key 决定
            key,
            message
        );

        // 添加 Header（用于消费者过滤）
        record.headers().add("channels",
            String.join(",", message.getRouting().getChannels()).getBytes());
        record.headers().add("priority",
            message.getPriority().getBytes());

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send message: {}", message.getMessageId(), ex);
            } else {
                log.info("Message sent to partition: {}",
                    result.getRecordMetadata().partition());
            }
        });
    }

    private String determineMessageKey(Message message) {
        return switch (message.getPriority()) {
            case "high" -> message.getRouting().getChannels().get(0);
            case "normal" -> message.getRecipient().getUserId();
            case "low" -> UUID.randomUUID().toString();
            default -> message.getRecipient().getUserId();
        };
    }
}
```

**消息格式**：

```json
{
  "messageId": "msg_20260302_123456",
  "tenantId": "tenant_123",
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
  },
  "createdAt": "2026-03-02T10:30:00Z"
}
```

### 2. messagepulse.receipts（回执 Topic）

**用途**：Channel Skill 发送完成后，将回执写入此 Topic。

**配置参数**：

```yaml
Topic: messagepulse.receipts
Partitions: 10
Replication Factor: 3
Retention: 7 days
Compression: lz4
```

**消息格式**：

```json
{
  "messageId": "msg_20260302_123456",
  "channel": "sms",
  "recipient": "13800138000",
  "status": "DELIVERED",
  "statusMessage": "Message delivered successfully",
  "sentAt": "2026-03-02T10:30:01Z",
  "deliveredAt": "2026-03-02T10:30:03Z",
  "durationMs": 2000,
  "externalMessageId": "sms_ext_123",
  "metadata": {
    "provider": "aliyun",
    "cost": 0.05
  },
  "errorCode": null,
  "errorMessage": null,
  "retryable": false
}
```

**消费者代码示例**：

```java
@Service
public class ReceiptConsumer {

    @KafkaListener(
        topics = "messagepulse.receipts",
        groupId = "messagepulse-core-receipt",
        concurrency = "5"
    )
    public void consumeReceipt(
        @Payload MessageReceipt receipt,
        Acknowledgment ack
    ) {
        try {
            // 更新消息状态
            messageStateService.updateChannelStatus(
                receipt.getMessageId(),
                receipt.getChannel(),
                receipt.getStatus(),
                receipt
            );

            // 如果所有渠道都完成，更新整体状态
            messageStateService.checkAndUpdateOverallStatus(receipt.getMessageId());

            // 记录计费信息
            if (receipt.getStatus() == DeliveryStatus.DELIVERED) {
                billingService.recordBilling(receipt);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process receipt: {}", receipt.getMessageId(), e);
        }
    }
}
```

### 3. messagepulse.dlq（死信队列 Topic）

**用途**：存储多次重试失败的消息，等待人工介入。

**配置参数**：

```yaml
Topic: messagepulse.dlq
Partitions: 5
Replication Factor: 3
Retention: 30 days
Compression: lz4
```

**消息格式**：

```json
{
  "originalMessage": { /* 原始消息 */ },
  "failureReason": "Rate limit exceeded after 3 retries",
  "retryCount": 3,
  "lastError": "HTTP 429: Too Many Requests",
  "firstFailedAt": "2026-03-02T10:30:00Z",
  "lastFailedAt": "2026-03-02T10:35:00Z",
  "channel": "sms"
}
```

## 消费者组设计

### 消费者组策略

每个 Channel Skill 使用独立的消费者组，从 `messagepulse.messages` Topic 消费消息。

```
┌─────────────────────────────────────────────────────────┐
│  messagepulse.messages (20 partitions)                  │
└─────────────────────────────────────────────────────────┘
                         ↓
        ┌────────────────┼────────────────┐
        │                │                │
┌───────▼──────┐  ┌──────▼──────┐  ┌─────▼───────┐
│ SMS Skill    │  │ Email Skill │  │ Feishu Skill│
│ Consumer     │  │ Consumer    │  │ Consumer    │
│ Group:       │  │ Group:      │  │ Group:      │
│ mp.sms       │  │ mp.email    │  │ mp.feishu   │
└──────────────┘  └─────────────┘  └─────────────┘
```

### 消费者配置

**SMS Skill 消费者配置**：

```yaml
spring:
  kafka:
    consumer:
      group-id: messagepulse.sms
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 500
      properties:
        # 消息过滤：只消费包含 "sms" 渠道的消息
        # 注意：Kafka 本身不支持服务端过滤，需要在消费者端过滤
```

**消费者代码示例**：

```java
@Service
public class SmsSkillConsumer {

    @KafkaListener(
        topics = "messagepulse.messages",
        groupId = "messagepulse.sms",
        concurrency = "10"
    )
    public void consume(
        @Payload Message message,
        @Header(value = "channels", required = false) String channels,
        Acknowledgment ack
    ) {
        // 过滤：只处理包含 "sms" 渠道的消息
        if (channels == null || !channels.contains("sms")) {
            ack.acknowledge();
            return;
        }

        try {
            // 发送短信
            smsSender.send(message);

            // 发送回执
            receiptProducer.sendReceipt(
                message.getMessageId(),
                "sms",
                DeliveryStatus.DELIVERED
            );

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send SMS: {}", message.getMessageId(), e);
            // 不提交 offset，消息会重新消费
        }
    }
}
```

### 消费者并发度

根据分区数和负载情况调整并发度：

| Skill | 分区数 | 并发度 | 说明 |
|-------|--------|--------|------|
| SMS | 20 | 10 | 高负载渠道 |
| Email | 20 | 5 | 中等负载 |
| Feishu | 20 | 3 | 低负载 |

**并发度配置**：

```java
@KafkaListener(
    topics = "messagepulse.messages",
    groupId = "messagepulse.sms",
    concurrency = "10"  // 10 个线程并发消费
)
```

## 分区与负载均衡

### 分区分配策略

Kafka 支持多种分区分配策略：

1. **RangeAssignor**（默认）：按范围分配
2. **RoundRobinAssignor**：轮询分配
3. **StickyAssignor**：粘性分配（推荐）

**配置**：

```yaml
spring:
  kafka:
    consumer:
      properties:
        partition.assignment.strategy: org.apache.kafka.clients.consumer.StickyAssignor
```

### 动态扩缩容

当 Skill 实例数量变化时，Kafka 自动重新分配分区：

```
初始状态（2 个 SMS Skill 实例）：
SMS-1: Partition 0-9
SMS-2: Partition 10-19

扩容后（3 个 SMS Skill 实例）：
SMS-1: Partition 0-6
SMS-2: Partition 7-13
SMS-3: Partition 14-19
```

## 消息顺序保证

### 用户级顺序

通过将 `userId` 作为消息键，保证同一用户的消息发送到同一分区，从而保证顺序。

```java
// 生产者
String key = message.getRecipient().getUserId();
kafkaTemplate.send("messagepulse.messages", key, message);

// 消费者
// 同一分区的消息按顺序消费
```

### 全局顺序

如果需要全局顺序（不推荐，性能差）：
- 设置 Topic 只有 1 个分区
- 消费者并发度设置为 1

## 消息重试机制

### 重试策略

```java
@Configuration
public class KafkaRetryConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Message>
        kafkaListenerContainerFactory(
            ConsumerFactory<String, Message> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Message> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 重试配置
        factory.setCommonErrorHandler(
            new DefaultErrorHandler(
                new FixedBackOff(1000L, 3L)  // 1秒间隔，最多重试3次
            )
        );

        return factory;
    }
}
```

### 死信队列

重试失败后，消息进入死信队列：

```java
@Bean
public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
    KafkaTemplate<String, Message> kafkaTemplate
) {
    return new DeadLetterPublishingRecoverer(
        kafkaTemplate,
        (record, ex) -> new TopicPartition("messagepulse.dlq", -1)
    );
}
```

## 性能优化

### 生产者优化

```yaml
spring:
  kafka:
    producer:
      # 批量发送
      batch-size: 16384        # 16KB
      linger-ms: 10            # 等待 10ms 凑批

      # 压缩
      compression-type: lz4    # lz4 压缩，平衡压缩率和性能

      # 可靠性
      acks: all                # 所有副本确认
      retries: 3               # 重试 3 次

      # 缓冲区
      buffer-memory: 33554432  # 32MB
```

### 消费者优化

```yaml
spring:
  kafka:
    consumer:
      # 批量拉取
      max-poll-records: 500    # 每次拉取 500 条
      fetch-min-size: 1024     # 最小拉取 1KB
      fetch-max-wait: 500      # 最多等待 500ms

      # 会话超时
      session-timeout-ms: 30000
      heartbeat-interval-ms: 3000
```

### Broker 优化

```properties
# server.properties

# 副本同步
num.replica.fetchers=4
replica.lag.time.max.ms=10000

# 日志段
log.segment.bytes=1073741824  # 1GB
log.retention.hours=168       # 7 天

# 压缩
compression.type=lz4

# 网络线程
num.network.threads=8
num.io.threads=8
```

## 监控指标

### 生产者指标

```java
// 发送速率
messagepulse_kafka_producer_records_sent_total

// 发送延迟
messagepulse_kafka_producer_record_send_latency_ms

// 失败率
messagepulse_kafka_producer_records_failed_total
```

### 消费者指标

```java
// 消费速率
messagepulse_kafka_consumer_records_consumed_total

// 消费延迟（Lag）
messagepulse_kafka_consumer_lag

// 重平衡次数
messagepulse_kafka_consumer_rebalance_total
```

### Kafka 集群指标

- **Under-replicated Partitions**：副本不足的分区数
- **Offline Partitions**：离线分区数
- **Active Controller Count**：活跃控制器数量（应为 1）
- **Request Handler Avg Idle Percent**：请求处理器空闲率

## 故障处理

### 消费者故障

当消费者崩溃时：
1. Kafka 检测到心跳超时
2. 触发 Rebalance
3. 其他消费者接管分区
4. 从上次提交的 offset 继续消费

### Broker 故障

当 Broker 故障时：
1. 副本选举新的 Leader
2. 生产者和消费者自动切换到新 Leader
3. 数据不丢失（Replication Factor = 3）

### 网络分区

当发生网络分区时：
- 生产者：重试机制保证消息最终发送成功
- 消费者：可能出现重复消费（需要幂等性保证）

## Kafka 集群部署

### 3 节点集群

```yaml
# docker-compose.yml
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka-1:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2

  kafka-2:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2

  kafka-3:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 3
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-3:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
```

## 与其他知识文档的关系

- **系统架构** → `02-architecture.md`：Kafka 在架构中的位置
- **技术栈详解** → `03-tech-stack.md`：Kafka 的技术选型理由
- **Channel Skills** → `09-channel-skills.md`：Skill 如何消费 Kafka 消息
- **监控设计** → `10-monitoring.md`：Kafka 监控指标
