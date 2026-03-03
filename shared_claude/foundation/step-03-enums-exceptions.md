# Step 03 - 枚举和异常创建记录

## 创建时间
2026-03-02

## 枚举类清单 (enums/)

| 文件 | 枚举值 | 说明 |
|------|--------|------|
| `MessageStatus.java` | PENDING, ROUTING, SENDING, SENT, FAILED, REVOKED | 消息状态 |
| `DeliveryStatus.java` | SUCCESS, FAILED, PARTIAL | 投递状态 |
| `RoutingMode.java` | EXPLICIT, IMPLICIT, AUTO | 路由模式 |
| `RoutingStrategy.java` | FAILOVER, LOAD_BALANCE, BROADCAST | 路由策略 |
| `Priority.java` | LOW, NORMAL, HIGH, URGENT | 优先级 |
| `ConsistencyStrategy.java` | EVENTUAL, AT_LEAST_ONE, ALL_OR_NONE, PRIORITY_ORDER | 一致性策略 |
| `ChannelType.java` | SMS, EMAIL, FEISHU, WECHAT, SLACK, DINGTALK, WEBHOOK, PUSH | 渠道类型 |
| `ErrorCode.java` | 1xxx-7xxx | 错误码（含code和message） |

## 异常类清单 (exception/)

| 文件 | 错误码 | 说明 |
|------|--------|------|
| `MessagePulseException.java` | - | 基础异常类，包含ErrorCode |
| `MessageNotFoundException.java` | MESSAGE_NOT_FOUND | 消息不存在 |
| `DuplicateMessageException.java` | DUPLICATE_MESSAGE | 重复消息 |
| `RateLimitExceededException.java` | RATE_LIMIT_EXCEEDED | 超出限流 |
| `ChannelNotAvailableException.java` | CHANNEL_NOT_AVAILABLE | 渠道不可用 |
| `AuthenticationException.java` | AUTHENTICATION_FAILED | 认证失败 |
| `AuthorizationException.java` | INSUFFICIENT_PERMISSIONS | 权限不足 |

## 常量类清单 (constant/)

| 文件 | 说明 |
|------|------|
| `KafkaTopics.java` | Kafka Topic 常量（send, status, receipt, retry, dlq, heartbeat, billing） |
| `RedisKeys.java` | Redis Key 前缀常量（dedup, status, ratelimit, apikey, skill, routing, tenant） |

## 设计说明
- ErrorCode 使用 Lombok @Getter，包含 code 和 message
- 所有业务异常继承 MessagePulseException
- 常量类使用 final class + private constructor 防止实例化
