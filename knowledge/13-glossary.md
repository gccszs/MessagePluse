# 术语表

本文档定义 MessagePulse 2.0 项目中使用的专有名词和概念。

## A

### API Key
用于认证和授权的密钥对，格式为 `ak_xxx.keySecret`。每个 API Key 关联一个租户，并具有特定的权限范围（Scopes）和渠道权限。

### API Key ID
API Key 的公开标识部分，格式为 `ak_` + 32位随机字符串，例如 `ak_abc123def456`。

### API Key Secret
API Key 的私密部分，客户端持有明文，服务端使用 BCrypt 加密存储。

## B

### BloomFilter（布隆过滤器）
一种空间效率极高的概率型数据结构，用于判断元素是否在集合中。MessagePulse 使用双缓冲布隆过滤器实现消息去重的第一级检查。

### Batch Task（批量任务）
通过 XXL-Job 调度的批量消息发送任务，用于向大量用户群发通知。

## C

### Channel（渠道）
消息发送的目标渠道，如短信（sms）、邮件（email）、飞书（feishu）、微信（wechat）等。

### Channel Skill
独立的渠道实现服务，每个渠道作为一个 Skill 运行，负责消费 Kafka 消息并调用渠道服务商 API 发送消息。

### Consistency Strategy（一致性策略）
多渠道投递时的一致性保证策略，包括：
- **EVENTUAL_CONSISTENCY**：最终一致性
- **AT_LEAST_ONE**：至少一个成功
- **ALL_OR_NONE**：全部成功或全部失败
- **PRIORITY_ORDER**：按优先级顺序

### Consumer Group（消费者组）
Kafka 消费者组，每个 Channel Skill 使用独立的消费者组从 `messagepulse.messages` Topic 消费消息。

## D

### Dead Letter Queue (DLQ)（死信队列）
存储多次重试失败的消息的 Kafka Topic（`messagepulse.dlq`），等待人工介入处理。

### Deduplication（去重）
防止重复消息发送的机制，MessagePulse 采用三级去重：布隆过滤器 → Redis → MySQL。

### Delivery Status（投递状态）
消息的投递状态，包括：
- **PENDING**：等待处理
- **PROCESSING**：正在处理
- **SENT_TO_CHANNEL**：已发送到渠道
- **DELIVERED**：已送达
- **FAILED**：发送失败
- **REVOKED**：已撤回
- **EXPIRED**：已过期
- **DEAD_LETTER**：在死信队列中

### Double Buffer（双缓冲）
布隆过滤器的轮换机制，通过维护两个过滤器（活跃和备用）并定期轮换，解决布隆过滤器不支持删除元素的问题。

## E

### Explicit Routing（显式路由）
消息发送时明确指定目标渠道的路由模式，适用于高优先级消息。

## F

### False Positive（假阳性）
布隆过滤器可能出现的误判，即判断元素存在但实际不存在。MessagePulse 配置的误判率为 0.01%。

### Filter Expression（过滤表达式）
Skill 配置中的消息过滤条件，例如 `channels contains 'sms'` 表示只处理包含短信渠道的消息。

## G

### Grafana Dashboard（Grafana 面板）
基于 Grafana 的可视化监控面板，展示消息发送趋势、延迟分布、错误率等指标。

## H

### Heartbeat（心跳）
Channel Skill 定期（每 15 秒）向 Core 发送的健康检查信号，用于服务发现和故障检测。

## I

### Implicit Routing（隐式路由）
根据策略引擎自动选择目标渠道的路由模式，适用于普通优先级消息。

## K

### Kafka Partition（Kafka 分区）
Kafka Topic 的分区，MessagePulse 的 `messagepulse.messages` Topic 有 20 个分区，支持并行消费。

### Kafka Topic
Kafka 中的消息主题，MessagePulse 使用的 Topic：
- `messagepulse.messages`：主消息 Topic
- `messagepulse.receipts`：回执 Topic
- `messagepulse.dlq`：死信队列 Topic

## M

### Message（消息）
MessagePulse 处理的基本单元，包含内容、路由配置、接收者信息等。

### Message ID
消息的唯一标识符，格式为 `msg_` + 时间戳 + 随机字符串，例如 `msg_20260302_123456`。

### Message Receipt（消息回执）
Channel Skill 发送完成后生成的回执信息，包含发送状态、耗时、外部消息 ID 等。

### Message Template（消息模板）
预定义的消息格式，支持多语言和多渠道，使用变量占位符（如 `{{code}}`）。

## P

### Priority（优先级）
消息的优先级，影响路由策略和处理顺序：
- **high**：高优先级
- **normal**：普通优先级
- **low**：低优先级

### Prometheus Metrics（Prometheus 指标）
用于监控的时序数据指标，如 `messagepulse.messages.sent_total`、`messagepulse.messages.delivery_time_seconds` 等。

## Q

### QPS (Queries Per Second)
每秒查询数，MessagePulse 的性能目标是接口层 QPS ≥ 12000。

## R

### Rate Limiting（限流）
流量控制机制，支持 API Key 级别、租户级别、渠道级别的限流。

### Recipient（接收者）
消息的接收者信息，包含 userId、phone、email、feishuUserId 等字段。

### Replication Factor（副本因子）
Kafka Topic 的副本数量，MessagePulse 配置为 3，保证数据可靠性。

### Routing（路由）
消息的路由配置，包含路由模式（mode）、目标渠道（channels）、投递策略（strategy）。

## S

### Scope（权限范围）
API Key 的细粒度权限，如 `message:send`、`message:query`、`message:revoke` 等。

### Skill Config（Skill 配置）
Channel Skill 的静态配置，存储在 `skill_configs` 表中，包含渠道名称、消费者组、Topic、过滤表达式等。

### Skill Instance（Skill 实例）
Channel Skill 的运行实例，动态注册到 `skill_instances` 表中，包含实例 ID、地址、端口、心跳时间等。

### SkyWalking
分布式链路追踪系统，用于追踪消息从接收到发送的完整链路。

## T

### Tenant（租户）
多租户架构中的租户，每个租户拥有独立的数据和配置，通过 `tenant_id` 字段隔离。

### Tenant Isolation（租户隔离）
确保不同租户的数据和资源相互隔离的机制，包括数据隔离、权限隔离、配额隔离。

## X

### XXL-Job
分布式任务调度平台，MessagePulse 使用 XXL-Job 实现定时任务、批量任务、延迟任务。

## 缩写

| 缩写 | 全称 | 说明 |
|------|------|------|
| AI | Artificial Intelligence | 人工智能 |
| API | Application Programming Interface | 应用程序接口 |
| DLQ | Dead Letter Queue | 死信队列 |
| DTO | Data Transfer Object | 数据传输对象 |
| FPP | False Positive Probability | 假阳性概率 |
| JPA | Java Persistence API | Java 持久化 API |
| JWT | JSON Web Token | JSON Web 令牌 |
| P50/P95/P99 | Percentile 50/95/99 | 第 50/95/99 百分位数 |
| QPS | Queries Per Second | 每秒查询数 |
| REST | Representational State Transfer | 表述性状态转移 |
| SDK | Software Development Kit | 软件开发工具包 |
| TTL | Time To Live | 生存时间 |

## 业务术语

### 消息生命周期
消息从创建到最终状态的完整过程：
```
创建 → 去重检查 → 路由决策 → 发送到 Kafka →
Skill 消费 → 调用渠道 API → 发送回执 → 状态更新
```

### 三级去重
MessagePulse 的去重机制：
1. **第一级**：布隆过滤器（内存，快速过滤）
2. **第二级**：Redis（网络，精确检查）
3. **第三级**：MySQL（磁盘，唯一约束兜底）

### 分阶段撤回
根据消息当前状态采取不同的撤回策略：
- **PENDING**：直接标记为 REVOKED
- **PROCESSING**：尝试拦截 + 通知 Skill
- **SENT_TO_CHANNEL**：通知 Skill 调用渠道撤回 API
- **DELIVERED**：标记为 REVOKED + 发送"已撤回"通知

### 分级路由
根据消息优先级和业务场景采用不同的路由策略：
- **High Priority**：显式路由 + 审批机制
- **Normal Priority**：隐式路由 + 策略引擎
- **Low Priority**：自动路由 + 智能决策

## 技术术语

### 双缓冲布隆过滤器
通过维护两个布隆过滤器并定期轮换，解决布隆过滤器不支持删除元素的问题。时间窗口为 5 分钟。

### 消息键策略
Kafka 消息的分区键选择策略：
- **High Priority**：使用 `channel` 作为 key
- **Normal Priority**：使用 `userId` 作为 key
- **Low Priority**：使用随机值作为 key

### 冷热数据分离
根据数据访问频率分层存储：
- **热数据**（7 天）：MySQL
- **温数据**（30 天）：Elasticsearch（可选）
- **冷数据**（90 天+）：对象存储（可选）

## 与其他知识文档的关系

本术语表是所有知识文档的索引，帮助快速理解项目中的专有名词。建议在阅读其他文档时参考本术语表。
