# 技术栈详解

## 技术栈总览

MessagePulse 2.0 采用现代化的 Java 技术栈，结合成熟的开源组件构建。

```
┌─────────────────────────────────────────────────────────┐
│  应用层                                                  │
│  Spring Boot 3.x + Java 17                              │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  消息队列                                                │
│  Apache Kafka 3.x                                       │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  数据存储                                                │
│  MySQL 8.x + Redis 7.x                                  │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  可观测性                                                │
│  Prometheus + Grafana + SkyWalking                      │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  容器化                                                  │
│  Docker + Docker Compose                                │
└─────────────────────────────────────────────────────────┘
```

## 核心框架

### Spring Boot 3.x

**选型理由**：
- 成熟的企业级框架，生态完善
- 内置 Tomcat，简化部署
- 自动配置，开发效率高
- 与 Spring 生态无缝集成

**使用方式**：
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<dependencies>
    <!-- Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Actuator (监控) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

**关键配置**：
```yaml
spring:
  application:
    name: messagepulse-core

  datasource:
    url: jdbc:mysql://localhost:3306/messagepulse
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQL8Dialect

  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
```

### Java 17

**选型理由**：
- LTS 版本，长期支持
- 性能提升（相比 Java 8）
- 新语言特性（Records、Pattern Matching、Sealed Classes）
- Spring Boot 3.x 要求 Java 17+

**使用的新特性**：
```java
// Records（数据传输对象）
public record MessageRequest(
    String content,
    Recipient recipient,
    List<String> channels,
    String priority
) {}

// Pattern Matching for instanceof
if (obj instanceof Message message) {
    return message.getMessageId();
}

// Text Blocks（多行字符串）
String sql = """
    SELECT * FROM messages
    WHERE tenant_id = ?
    AND status = ?
    ORDER BY created_at DESC
    """;
```

## 消息队列

### Apache Kafka 3.x

**选型理由**：
- 高吞吐量：支持百万级 QPS
- 持久化：消息可重放
- 分区机制：天然支持并行处理
- 消费者组：支持多 Skill 独立消费
- 成熟稳定：大规模生产环境验证

**核心配置**：
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

    # Producer 配置
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all  # 所有副本确认
      retries: 3
      batch-size: 16384
      linger-ms: 10
      compression-type: lz4

    # Consumer 配置
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: messagepulse-core
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 500
      properties:
        spring.json.trusted.packages: com.messagepulse.*
```

**Topic 设计**：
```java
// Topic 配置
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic messagesTopicConfig() {
        return TopicBuilder.name("messagepulse.messages")
            .partitions(20)
            .replicas(3)
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7天
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "lz4")
            .build();
    }

    @Bean
    public NewTopic receiptsTopicConfig() {
        return TopicBuilder.name("messagepulse.receipts")
            .partitions(10)
            .replicas(3)
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
            .build();
    }
}
```

**生产者示例**：
```java
@Service
public class MessageProducer {

    private final KafkaTemplate<String, Message> kafkaTemplate;

    public void sendMessage(Message message) {
        // 使用 userId 作为 key，保证同一用户的消息顺序
        String key = message.getRecipient().getUserId();

        kafkaTemplate.send("messagepulse.messages", key, message)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send message: {}", message.getMessageId(), ex);
                } else {
                    log.info("Message sent: {}, partition: {}",
                        message.getMessageId(),
                        result.getRecordMetadata().partition());
                }
            });
    }
}
```

**消费者示例**：
```java
@Service
public class MessageConsumer {

    @KafkaListener(
        topics = "messagepulse.messages",
        groupId = "messagepulse-core",
        concurrency = "10"
    )
    public void consume(
        @Payload Message message,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        Acknowledgment ack
    ) {
        try {
            // 处理消息
            messageService.processMessage(message);

            // 手动提交 offset
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process message: {}", message.getMessageId(), e);
            // 不提交 offset，消息会重新消费
        }
    }
}
```

## 数据存储

### MySQL 8.x

**选型理由**：
- 成熟稳定的关系型数据库
- 支持 JSON 字段类型
- 事务支持，保证数据一致性
- 丰富的索引类型
- 主从复制，支持读写分离

**使用方式**：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/messagepulse?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver

    # HikariCP 连接池配置
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**JPA 实体示例**：
```java
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class Message {

    @Id
    @Column(length = 64)
    private String messageId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MessageStatus status;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON", nullable = false)
    private MessageContent content;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON", nullable = false)
    private Routing routing;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;
}
```

### Redis 7.x

**选型理由**：
- 高性能内存数据库
- 支持多种数据结构（String、Hash、Set、ZSet）
- 支持过期时间，自动清理
- 支持 Lua 脚本，原子操作
- 主从复制 + 哨兵模式，高可用

**使用场景**：
1. **去重**：存储消息 ID，10 分钟过期
2. **限流**：令牌桶算法，基于 Lua 脚本
3. **缓存**：API Key、用户信息、配置信息
4. **分布式锁**：消息撤回时的并发控制

**配置**：
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      password:
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 2000ms
```

**使用示例**：
```java
@Service
public class DedupService {

    private final RedisTemplate<String, String> redisTemplate;

    // 去重检查
    public boolean isDuplicate(String messageId) {
        String key = "msg:dedup:" + messageId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // 标记为已处理
    public void markAsProcessed(String messageId) {
        String key = "msg:dedup:" + messageId;
        redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(10));
    }

    // 限流（Lua 脚本）
    public boolean checkRateLimit(String apiKeyId, int limit, int windowSeconds) {
        String key = "ratelimit:apikey:" + apiKeyId;

        String luaScript = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('incr', key)
            if current == 1 then
                redis.call('expire', key, window)
            end
            return current <= limit
            """;

        DefaultRedisScript<Boolean> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(Boolean.class);

        return Boolean.TRUE.equals(
            redisTemplate.execute(script,
                Collections.singletonList(key),
                String.valueOf(limit),
                String.valueOf(windowSeconds))
        );
    }
}
```

## 可观测性

### Prometheus + Grafana

**选型理由**：
- Prometheus：时序数据库，指标采集和存储
- Grafana：可视化面板，支持丰富的图表
- 云原生标准，与 Kubernetes 无缝集成
- 强大的查询语言（PromQL）

**集成方式**：
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

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
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
```

**自定义指标**：
```java
@Service
public class MessageMetricsService {

    private final MeterRegistry meterRegistry;

    // 消息发送计数
    public void recordMessageSent(String channel, String status) {
        Counter.builder("messagepulse.messages.sent")
            .tag("channel", channel)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    // 消息延迟
    public void recordDeliveryTime(String channel, long durationMs) {
        Timer.builder("messagepulse.messages.delivery_time")
            .tag("channel", channel)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));
    }

    // 去重命中率
    public void recordDedupHit(String level) {
        Counter.builder("messagepulse.deduplication.hit")
            .tag("level", level)  // bloom, redis, mysql
            .register(meterRegistry)
            .increment();
    }
}
```

### SkyWalking

**选型理由**：
- 分布式链路追踪
- 自动埋点，无侵入
- 支持多种语言和框架
- 服务拓扑图，依赖分析

**集成方式**：
```bash
# 启动时添加 Java Agent
java -javaagent:/path/to/skywalking-agent.jar \
     -Dskywalking.agent.service_name=messagepulse-core \
     -Dskywalking.collector.backend_service=localhost:11800 \
     -jar messagepulse-core.jar
```

## 容器化

### Docker

**Dockerfile 示例**：
```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/messagepulse-core.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose

**用途**：本地开发和测试环境一键启动

**核心服务**：
- messagepulse-core
- kafka + zookeeper
- mysql
- redis
- prometheus + grafana

详细配置见 `12-deployment.md`

## 其他重要依赖

### Guava（布隆过滤器）

```xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>32.1.3-jre</version>
</dependency>
```

### Jackson（JSON 序列化）

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

### Lombok（代码简化）

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

### Hibernate Validator（参数校验）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

## 技术选型对比

### 消息队列选型

| 特性 | Kafka | RabbitMQ | RocketMQ |
|------|-------|----------|----------|
| 吞吐量 | 极高 | 中等 | 高 |
| 延迟 | 低 | 极低 | 低 |
| 持久化 | 是 | 是 | 是 |
| 消息重放 | 支持 | 不支持 | 支持 |
| 运维复杂度 | 中等 | 低 | 中等 |
| 生态成熟度 | 极高 | 高 | 中等 |
| **选择** | ✅ | ❌ | ❌ |

### 缓存选型

| 特性 | Redis | Memcached | Hazelcast |
|------|-------|-----------|-----------|
| 数据结构 | 丰富 | 简单 | 丰富 |
| 持久化 | 支持 | 不支持 | 支持 |
| 集群 | 支持 | 不支持 | 支持 |
| Lua 脚本 | 支持 | 不支持 | 不支持 |
| 成熟度 | 极高 | 高 | 中等 |
| **选择** | ✅ | ❌ | ❌ |

## 与其他知识文档的关系

- **系统架构** → `02-architecture.md`：技术栈在架构中的位置
- **Kafka 设计** → `06-kafka-design.md`：Kafka 的详细使用
- **去重引擎** → `07-dedup-engine.md`：Guava BloomFilter 的使用
- **监控设计** → `10-monitoring.md`：Prometheus + Grafana 的详细配置
