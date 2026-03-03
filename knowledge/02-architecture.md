# 系统架构

## 整体架构

MessagePulse 2.0 采用三层架构设计，通过 Kafka 实现层与层之间的解耦。

### 架构总览

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

## 分层设计

### 1. 接入层

负责接收外部系统的消息请求，完成身份验证和初步校验。

**核心组件**：
- **REST API**：消息发送、状态查询、配置管理
- **Kafka Producer**：接收 AI 系统通过 Kafka 发送的消息
- **API 认证**：API Key / JWT Token 验证

**数据流**：
```
外部系统 → REST API → 认证过滤器 → 权限检查 → 消息入队
```

**设计决策**：
- 接入层采用同步 HTTP + 异步 Kafka 双通道
- HTTP 请求快速返回 messageId，不等待实际发送
- 通过 Kafka 实现接入层与处理层的解耦

### 2. 处理层

核心业务逻辑层，负责消息的去重、路由、状态管理等。

**核心组件**：
- **路由引擎**：分级路由机制（显式、隐式、自动）
- **去重引擎**：三级去重（布隆过滤器 + Redis + MySQL）
- **撤回引擎**：分阶段撤回
- **一致性引擎**：策略化一致性处理

**数据流**：
```
消息入队 → 去重检查 → 路由决策 → 发送到 Channel Topic → 状态更新
```

**设计决策**：
- 去重在入队阶段完成，减少无效消息流转
- 路由决策支持多种策略，可按消息优先级选择
- 状态机管理消息完整生命周期

### 3. 存储层

数据持久化和缓存层。

**核心组件**：
- **Kafka 3 节点集群**：消息队列，核心数据流转通道
- **Redis**：缓存、去重、限流
- **MySQL**：消息状态、配置、租户数据

**数据分布**：
| 数据类型 | 存储位置 | TTL | 用途 |
|---------|---------|-----|------|
| 消息去重标记 | Redis | 10 分钟 | 精确去重 |
| 消息状态 | MySQL | 永久 | 状态查询 |
| 渠道配置 | MySQL | 永久 | 配置管理 |
| API Key | MySQL + Redis | 永久 + 缓存 | 认证 |
| 限流计数 | Redis | 按窗口 | 流量控制 |
| 消息流转 | Kafka | 7 天 | 异步解耦 |

## 核心数据流

### 消息发送流程

```
1. 外部系统调用 POST /api/v1/messages
   ↓
2. 接入层：API Key 认证 + 权限检查
   ↓
3. 接入层：消息入队（写入 Kafka messagepulse.messages Topic）
   ↓
4. 处理层（Core Consumer）：消费消息
   ↓
5. 处理层：去重检查（布隆过滤器 → Redis → MySQL）
   ↓
6. 处理层：路由决策（确定目标渠道）
   ↓
7. 处理层：写入消息状态（MySQL）
   ↓
8. Channel Skill：消费渠道 Topic 的消息
   ↓
9. Channel Skill：调用渠道服务商 API 发送
   ↓
10. Channel Skill：发送回执到 messagepulse.receipts Topic
   ↓
11. Core 回执消费者：更新消息状态
```

### 消息撤回流程

```
1. 外部系统调用 POST /api/v1/messages/{messageId}/revoke
   ↓
2. 检查消息当前状态
   ↓
3. 根据状态执行不同撤回策略：
   - PENDING → 直接标记为 REVOKED
   - PROCESSING → 尝试拦截 + 通知 Skill
   - SENT_TO_CHANNEL → 通知 Skill 调用渠道撤回 API
   - DELIVERED → 标记为 REVOKED + 发送"已撤回"通知
```

### 回执处理流程

```
1. Channel Skill 发送完成后
   ↓
2. 生成 MessageReceipt 对象
   ↓
3. 发送到 messagepulse.receipts Topic
   ↓
4. Core 回执消费者消费
   ↓
5. 更新 message_states 表
   ↓
6. 如果 AI 系统监听了 receipts Topic，通知 AI 系统
```

## 组件关系图

```
                    ┌──────────────┐
                    │  REST API    │
                    │  Controller  │
                    └──────┬───────┘
                           │
               ┌───────────┼───────────┐
               │           │           │
        ┌──────▼──────┐  ┌─▼──────┐ ┌──▼──────────┐
        │ AuthFilter  │  │ Message │ │ SkillConfig │
        │ (认证)      │  │ Service │ │ Service     │
        └──────┬──────┘  └─┬──────┘ └──┬──────────┘
               │           │           │
        ┌──────▼──────┐    │     ┌─────▼────────┐
        │ ApiKey      │    │     │ SkillConfig  │
        │ Repository  │    │     │ Repository   │
        └─────────────┘    │     └──────────────┘
                           │
               ┌───────────┼───────────┐
               │           │           │
        ┌──────▼──────┐ ┌──▼─────┐ ┌──▼──────────┐
        │ Dedup       │ │ Router │ │ StateMachine│
        │ Service     │ │ Engine │ │ Service     │
        └──────┬──────┘ └──┬─────┘ └──┬──────────┘
               │           │          │
        ┌──────▼──────┐    │     ┌────▼───────┐
        │ BloomFilter │    │     │ MessageState│
        │ + Redis     │    │     │ Repository  │
        └─────────────┘    │     └────────────┘
                           │
                    ┌──────▼───────┐
                    │ Kafka        │
                    │ Producer     │
                    └──────────────┘
```

## 模块边界

### Core 模块

```
com.messagepulse.core
├── config/          # 配置类
├── controller/      # REST API 控制器
├── service/         # 业务服务
│   ├── MessageService
│   ├── DedupService
│   ├── RouterService
│   ├── TemplateService
│   ├── RateLimiterService
│   └── BillingService
├── security/        # 认证授权
│   ├── ApiKeyAuthenticationFilter
│   └── SecurityConfig
├── kafka/           # Kafka 消费者/生产者
│   ├── MessageConsumer
│   ├── ReceiptConsumer
│   └── MessageProducer
├── entity/          # JPA 实体
├── repository/      # 数据访问
├── dto/             # 数据传输对象
└── enums/           # 枚举类
```

### Channel Skill 模块

```
com.messagepulse.skill.<channel>
├── config/          # Skill 配置
├── consumer/        # Kafka 消费者
├── sender/          # 渠道发送实现
├── heartbeat/       # 心跳服务
└── model/           # 数据模型
```

## 设计决策记录

### 决策 1：Kafka vs RabbitMQ

**选择**：Kafka

**理由**：
- 高吞吐量，支持 12000+ QPS 目标
- 消息持久化，支持消费者重放
- 多消费者组，支持不同 Skill 独立消费
- 分区机制，天然支持并行处理

### 决策 2：统一 Topic vs 每渠道独立 Topic

**选择**：统一 Topic `messagepulse.messages` + 多分区

**理由**：
- 简化发送方逻辑
- 通过消息键实现用户级顺序保证
- 消费者组机制实现渠道级隔离
- 减少 Topic 管理复杂度

### 决策 3：同步 HTTP vs 纯 Kafka 接入

**选择**：同步 HTTP + Kafka 双通道

**理由**：
- HTTP 对传统系统友好，降低接入门槛
- Kafka 对 AI 系统友好，高性能
- HTTP 快速返回 messageId，内部异步处理
- 兼顾易用性和性能

### 决策 4：Skill 运行模式

**选择**：独立进程 + Kafka 消费

**理由**：
- 独立部署，故障隔离
- 技术栈不限（Java、Python）
- 独立扩缩容
- 通过 Kafka 消费者组实现负载均衡

## 与其他知识文档的关系

- **技术栈详解** → `03-tech-stack.md`：详细介绍各技术选型
- **数据库设计** → `04-database-design.md`：存储层的表结构设计
- **API 设计** → `05-api-design.md`：接入层的 API 详细设计
- **Kafka 设计** → `06-kafka-design.md`：消息队列的详细设计
- **去重引擎** → `07-dedup-engine.md`：处理层的去重实现
- **Channel Skills** → `09-channel-skills.md`：Skill 层的详细设计
