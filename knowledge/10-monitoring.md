# 监控设计

## 监控架构概览

MessagePulse 2.0 采用多层监控体系，覆盖指标、日志、链路追踪三个维度。

```
┌─────────────────────────────────────────────────────────┐
│  监控层次                                                │
├─────────────────────────────────────────────────────────┤
│  1. Metrics（指标监控）                                  │
│     Prometheus + Grafana                                │
│     - QPS、延迟、成功率、错误率                         │
│                                                         │
│  2. Logging（日志监控）                                  │
│     ELK Stack                                           │
│     - 结构化日志、异常追踪                              │
│                                                         │
│  3. Tracing（链路追踪）                                  │
│     SkyWalking / Zipkin                                 │
│     - 请求链路、服务拓扑                                │
│                                                         │
│  4. Alerting（告警）                                     │
│     Prometheus AlertManager                             │
│     - 异常告警、阈值告警                                │
└─────────────────────────────────────────────────────────┘
```

## Prometheus 指标设计

### 指标命名规范

```
messagepulse.<module>.<metric_name>_<unit>
```

例如：
- `messagepulse.messages.sent_total`
- `messagepulse.messages.delivery_time_seconds`
- `messagepulse.deduplication.hit_total`

### 核心指标

#### 1. 消息发送指标

```java
// 消息发送计数
Counter.builder("messagepulse.messages.sent")
    .tag("channel", channel)       // sms, email, feishu
    .tag("status", status)         // success, failed
    .tag("tenant", tenantId)       // 租户ID
    .description("Total messages sent")
    .register(meterRegistry);

// 消息延迟分布
Timer.builder("messagepulse.messages.delivery_time")
    .tag("channel", channel)
    .publishPercentiles(0.5, 0.95, 0.99)
    .description("Message delivery time")
    .register(meterRegistry);

// 消息队列中的消息数量
Gauge.builder("messagepulse.messages.pending")
    .tag("channel", channel)
    .description("Pending messages in queue")
    .register(meterRegistry, pendingCount);
```

#### 2. 去重引擎指标

```java
// 去重命中率
Counter.builder("messagepulse.deduplication.hit")
    .tag("level", "bloom")  // bloom, redis
    .description("Deduplication hits")
    .register(meterRegistry);

// 布隆过滤器大小
Gauge.builder("messagepulse.bloom_filter.size")
    .description("Approximate element count in bloom filter")
    .register(meterRegistry, bloomFilterSize);

// 布隆过滤器误判率
Gauge.builder("messagepulse.bloom_filter.fpp")
    .description("Expected false positive probability")
    .register(meterRegistry, fpp);
```

#### 3. API 指标

```java
// HTTP 请求计数（Spring Boot Actuator 自动收集）
http_server_requests_seconds_count{
    method="POST",
    uri="/api/v1/messages",
    status="201"
}

// HTTP 请求延迟
http_server_requests_seconds_sum{
    method="POST",
    uri="/api/v1/messages",
    status="201"
}

// API Key 认证失败计数
Counter.builder("messagepulse.auth.failures")
    .tag("reason", "invalid_key")  // invalid_key, expired, disabled
    .description("Authentication failures")
    .register(meterRegistry);
```

#### 4. Kafka 指标

```java
// Kafka 消费者 Lag
messagepulse_kafka_consumer_lag{
    group="messagepulse-core",
    topic="messagepulse.messages",
    partition="0"
}

// Kafka 生产者发送速率
messagepulse_kafka_producer_records_sent_total

// Kafka 消费者消费速率
messagepulse_kafka_consumer_records_consumed_total
```

#### 5. 系统资源指标

```java
// JVM 内存使用
jvm_memory_used_bytes{area="heap"}
jvm_memory_used_bytes{area="nonheap"}

// JVM GC 次数和耗时
jvm_gc_pause_seconds_count
jvm_gc_pause_seconds_sum

// CPU 使用率
system_cpu_usage
process_cpu_usage

// 数据库连接池
hikaricp_connections_active
hikaricp_connections_idle
hikaricp_connections_max
```

### Prometheus 配置

**Spring Boot Actuator 配置**：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info
      base-path: /actuator
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        messagepulse.messages.delivery_time: true
        http.server.requests: true
      percentiles:
        messagepulse.messages.delivery_time: 0.5, 0.95, 0.99
        http.server.requests: 0.5, 0.95, 0.99
      sla:
        messagepulse.messages.delivery_time: 50ms, 100ms, 500ms
    tags:
      application: messagepulse-core
```

**Prometheus 服务端配置**：

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'messagepulse-core'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['messagepulse-core:8080']

  - job_name: 'sms-skill'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['sms-skill:8081']

  - job_name: 'email-skill'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['email-skill:8082']

  - job_name: 'feishu-skill'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['feishu-skill:8083']
```

## Grafana 面板设计

### Dashboard 1：消息概览

```
┌───────────────────────────────────────────────┐
│  MessagePulse - 消息概览                       │
├───────────────┬───────────────┬───────────────┤
│  总消息数      │  成功率        │  平均延迟      │
│  1,234,567    │  99.5%        │  15ms         │
├───────────────┴───────────────┴───────────────┤
│  [消息发送趋势图 - 按渠道分组]                  │
│  x: 时间  y: QPS                               │
│  --- sms  --- email  --- feishu                │
├───────────────────────────────────────────────┤
│  [延迟分布图]                                   │
│  x: 时间  y: 延迟(ms)                          │
│  --- P50  --- P95  --- P99                     │
├───────────────────────────────────────────────┤
│  [错误率趋势图]                                 │
│  x: 时间  y: 错误率(%)                         │
└───────────────────────────────────────────────┘
```

**PromQL 查询示例**：

```promql
# 消息发送 QPS（按渠道分组）
sum(rate(messagepulse_messages_sent_total[5m])) by (channel)

# 消息成功率
sum(rate(messagepulse_messages_sent_total{status="success"}[5m]))
/
sum(rate(messagepulse_messages_sent_total[5m]))

# P99 延迟
histogram_quantile(0.99,
  sum(rate(messagepulse_messages_delivery_time_seconds_bucket[5m]))
  by (le, channel)
)

# 错误率
sum(rate(messagepulse_messages_sent_total{status="failed"}[5m]))
/
sum(rate(messagepulse_messages_sent_total[5m]))
```

### Dashboard 2：去重引擎

```
┌───────────────────────────────────────────────┐
│  MessagePulse - 去重引擎                       │
├───────────────┬───────────────┬───────────────┤
│  去重命中率    │  布隆过滤器    │  Redis 去重    │
│  0.05%        │  元素数        │  Key 数量      │
├───────────────────────────────────────────────┤
│  [布隆过滤器 vs Redis 去重命中趋势]            │
├───────────────────────────────────────────────┤
│  [布隆过滤器元素数量变化]                       │
└───────────────────────────────────────────────┘
```

### Dashboard 3：系统资源

```
┌───────────────────────────────────────────────┐
│  MessagePulse - 系统资源                       │
├───────────────┬───────────────┬───────────────┤
│  CPU 使用率    │  内存使用      │  连接池        │
│  35%          │  2.1 GB / 4GB │  15/20 active │
├───────────────────────────────────────────────┤
│  [JVM Heap 内存趋势]                           │
├───────────────────────────────────────────────┤
│  [GC 暂停时间分布]                              │
├───────────────────────────────────────────────┤
│  [Kafka Consumer Lag]                          │
└───────────────────────────────────────────────┘
```

## 链路追踪

### SkyWalking 集成

**启动配置**：

```bash
java -javaagent:/path/to/skywalking-agent.jar \
     -Dskywalking.agent.service_name=messagepulse-core \
     -Dskywalking.collector.backend_service=skywalking-oap:11800 \
     -jar messagepulse-core.jar
```

**追踪粒度**：

```
外部请求 → API Controller → Service → Kafka Producer → Kafka Consumer → Skill Sender
    │           │              │            │                │               │
    └───────────┴──────────────┴────────────┴────────────────┴───────────────┘
                              完整链路追踪
```

### 自定义 Span

```java
@Service
public class MessageService {

    @Trace  // SkyWalking 自动追踪
    public void processMessage(Message message) {
        // 去重检查
        ActiveSpan.tag("messageId", message.getMessageId());
        ActiveSpan.tag("channel", String.join(",",
            message.getRouting().getChannels()));

        if (dedupService.isDuplicate(message.getMessageId())) {
            ActiveSpan.tag("deduplicated", "true");
            return;
        }

        // 路由决策
        // 发送到 Kafka
    }
}
```

## 告警规则

### AlertManager 配置

```yaml
# alertmanager.yml
route:
  receiver: 'default'
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h

  routes:
    - match:
        severity: critical
      receiver: 'pager'
    - match:
        severity: warning
      receiver: 'slack'

receivers:
  - name: 'default'
    webhook_configs:
      - url: 'http://webhook.example.com/alert'
  - name: 'pager'
    pagerduty_configs:
      - service_key: 'xxx'
  - name: 'slack'
    slack_configs:
      - api_url: 'https://hooks.slack.com/services/xxx'
```

### 告警规则

```yaml
# prometheus-rules.yml
groups:
  - name: messagepulse
    rules:
      # 消息成功率低于 99%
      - alert: LowMessageSuccessRate
        expr: |
          sum(rate(messagepulse_messages_sent_total{status="success"}[5m]))
          /
          sum(rate(messagepulse_messages_sent_total[5m]))
          < 0.99
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "消息成功率低于 99%"
          description: "当前成功率: {{ $value | humanizePercentage }}"

      # P99 延迟超过 500ms
      - alert: HighLatency
        expr: |
          histogram_quantile(0.99,
            sum(rate(messagepulse_messages_delivery_time_seconds_bucket[5m]))
            by (le)
          ) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "消息 P99 延迟超过 500ms"

      # Kafka Consumer Lag 过高
      - alert: HighConsumerLag
        expr: |
          sum(kafka_consumer_lag) by (group) > 10000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Kafka Consumer Lag 超过 10000"

      # Skill 实例不可用
      - alert: SkillInstanceDown
        expr: |
          up{job=~".*-skill"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Skill 实例不可用: {{ $labels.instance }}"

      # 内存使用率高
      - alert: HighMemoryUsage
        expr: |
          jvm_memory_used_bytes{area="heap"}
          /
          jvm_memory_max_bytes{area="heap"}
          > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM Heap 内存使用率超过 85%"
```

## 日志设计

### 结构化日志

```yaml
# logback-spring.xml
logging:
  level:
    com.messagepulse: INFO
    org.apache.kafka: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### 关键日志点

```java
// 消息发送日志
log.info("Message sent: messageId={}, channel={}, duration={}ms",
    messageId, channel, durationMs);

// 去重日志
log.debug("Dedup check: messageId={}, level={}, result={}",
    messageId, level, isDuplicate ? "duplicate" : "new");

// 认证日志
log.warn("Auth failed: keyId={}, reason={}", keyId, reason);

// 错误日志
log.error("Message delivery failed: messageId={}, channel={}, error={}",
    messageId, channel, errorMessage, exception);
```

## Docker Compose 监控配置

```yaml
# 监控服务
prometheus:
  image: prom/prometheus:v2.47.0
  ports:
    - "9090:9090"
  volumes:
    - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    - ./monitoring/rules:/etc/prometheus/rules
    - prometheus_data:/prometheus

grafana:
  image: grafana/grafana:10.1.0
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
  volumes:
    - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
    - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources
    - grafana_data:/var/lib/grafana
  depends_on:
    - prometheus

alertmanager:
  image: prom/alertmanager:v0.26.0
  ports:
    - "9093:9093"
  volumes:
    - ./monitoring/alertmanager.yml:/etc/alertmanager/alertmanager.yml

volumes:
  prometheus_data:
  grafana_data:
```

## 与其他知识文档的关系

- **系统架构** → `02-architecture.md`：监控在架构中的位置
- **技术栈详解** → `03-tech-stack.md`：Prometheus/Grafana 的选型
- **去重引擎** → `07-dedup-engine.md`：去重引擎的监控指标
- **部署方案** → `12-deployment.md`：监控服务的部署配置
