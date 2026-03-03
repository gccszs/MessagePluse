# Channel Skills 设计

## 设计理念

Channel Skills 是 MessagePulse 2.0 的插件化渠道实现。每个渠道（短信、邮件、飞书等）作为独立的 Skill 运行，通过 Kafka 消费消息并调用渠道服务商 API 发送。

### 核心特点

- **独立部署**：每个 Skill 是独立的 Spring Boot 应用
- **技术栈无关**：Skill 可以用 Java、Python 等不同语言开发
- **按需激活**：通过配置启用/停用 Skill
- **水平扩展**：同一渠道可以部署多个 Skill 实例
- **故障隔离**：单个 Skill 故障不影响其他渠道

## 架构设计

### Skill 注册与发现

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

### Skill 生命周期

```
1. Skill 启动
   ↓
2. 从 Core 获取配置（GET /api/v1/skills/{channel}/config）
   ↓
3. 注册到服务注册表（POST /api/v1/skills/{channel}/register）
   ↓
4. 开始消费 Kafka 消息
   ↓
5. 定期发送心跳（POST /api/v1/skills/{channel}/heartbeat）
   ↓
6. 处理消息并发送回执
   ↓
7. 关闭时注销（DELETE /api/v1/skills/{channel}/instances/{skillId}）
```

## 配置实体

### SkillConfig（静态配置）

```java
@Entity
@Table(name = "skill_configs")
public class SkillConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_name", unique = true, nullable = false)
    private String channelName;        // sms, feishu, email

    @Column(name = "display_name", nullable = false)
    private String displayName;        // 短信, 飞书, 邮件

    @Column(name = "consumer_group", nullable = false)
    private String consumerGroup;      // messagepulse.sms

    @Column(nullable = false)
    private String topic;              // messagepulse.messages

    @Column(name = "filter_expression", nullable = false)
    private String filterExpression;   // channels contains 'sms'

    private Boolean enabled;

    @Type(JsonType.class)
    @Column(name = "channel_config", columnDefinition = "JSON")
    private Map<String, Object> channelConfig;
}
```

### SkillInstance（动态注册）

```java
@Entity
@Table(name = "skill_instances")
public class SkillInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", unique = true, nullable = false)
    private String skillId;            // sms-skill-1

    @Column(nullable = false)
    private String channel;            // sms

    @Column(nullable = false)
    private String address;            // 192.168.1.10

    @Column(nullable = false)
    private Integer port;              // 8081

    private String status;             // ACTIVE, INACTIVE

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;
}
```

## 心跳机制

### 心跳流程

```
Skill 实例                                Core
    │                                       │
    │── POST /heartbeat ──────────────────→ │
    │                                       │── 更新 last_heartbeat
    │← 200 OK ────────────────────────────│
    │                                       │
    │   ... 15秒后 ...                       │
    │                                       │
    │── POST /heartbeat ──────────────────→ │
    │← 200 OK ────────────────────────────│
    │                                       │
    │   ... Skill 崩溃 ...                   │
    │                                       │
    │                                       │── 30秒后检测到心跳超时
    │                                       │── 标记为 INACTIVE
```

### 心跳服务实现

```java
@Service
public class HeartbeatService {

    private final SkillInstanceRepository instanceRepository;

    /**
     * Skill 端：发送心跳
     */
    @Scheduled(fixedRate = 15000) // 每 15 秒
    public void sendHeartbeat() {
        restTemplate.postForObject(
            coreBaseUrl + "/api/v1/skills/{channel}/heartbeat",
            new HeartbeatRequest(skillId, address, port),
            Void.class,
            channelName
        );
    }

    /**
     * Core 端：检查心跳超时
     */
    @Scheduled(fixedRate = 30000) // 每 30 秒
    public void checkHeartbeatTimeout() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(30);

        List<SkillInstance> timedOut = instanceRepository
            .findByStatusAndLastHeartbeatBefore("ACTIVE", threshold);

        for (SkillInstance instance : timedOut) {
            instance.setStatus("INACTIVE");
            instanceRepository.save(instance);
            log.warn("Skill instance timed out: {} ({})",
                instance.getSkillId(), instance.getChannel());
        }
    }
}
```

## Skill 实现模板

### SMS Skill 完整实现

```java
@SpringBootApplication
public class SmsSkillApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmsSkillApplication.class, args);
    }
}
```

**消费者**：

```java
@Service
public class SmsSkillConsumer {

    private final SmsSender smsSender;
    private final ReceiptProducer receiptProducer;

    @KafkaListener(
        topics = "${messagepulse.topic:messagepulse.messages}",
        groupId = "${messagepulse.consumer-group:messagepulse.sms}",
        concurrency = "10"
    )
    public void consume(
        @Payload Message message,
        Acknowledgment ack
    ) {
        // 过滤：只处理包含 "sms" 渠道的消息
        if (!message.getRouting().getChannels().contains("sms")) {
            ack.acknowledge();
            return;
        }

        try {
            // 发送短信
            SmsSendResult result = smsSender.send(
                message.getRecipient().getPhone(),
                renderContent(message)
            );

            // 发送成功回执
            receiptProducer.sendReceipt(MessageReceipt.builder()
                .messageId(message.getMessageId())
                .channel("sms")
                .recipient(message.getRecipient().getPhone())
                .status(DeliveryStatus.DELIVERED)
                .sentAt(LocalDateTime.now())
                .deliveredAt(LocalDateTime.now())
                .durationMs(result.getDurationMs())
                .externalMessageId(result.getExternalId())
                .build());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("SMS send failed: {}", message.getMessageId(), e);

            // 发送失败回执
            receiptProducer.sendReceipt(MessageReceipt.builder()
                .messageId(message.getMessageId())
                .channel("sms")
                .status(DeliveryStatus.FAILED)
                .errorCode(determineErrorCode(e))
                .errorMessage(e.getMessage())
                .retryable(isRetryable(e))
                .build());

            ack.acknowledge();
        }
    }
}
```

**发送器**：

```java
@Component
public class SmsSender {

    @Value("${sms.provider:aliyun}")
    private String provider;

    public SmsSendResult send(String phoneNumber, String content) {
        long startTime = System.currentTimeMillis();

        // 调用短信服务商 API（示例：阿里云 SMS）
        SendSmsRequest request = new SendSmsRequest();
        request.setPhoneNumbers(phoneNumber);
        request.setSignName("MessagePulse");
        request.setTemplateCode("SMS_12345");
        request.setTemplateParam("{\"content\":\"" + content + "\"}");

        SendSmsResponse response = acsClient.getAcsResponse(request);

        long duration = System.currentTimeMillis() - startTime;

        return SmsSendResult.builder()
            .externalId(response.getBizId())
            .durationMs(duration)
            .success("OK".equals(response.getCode()))
            .build();
    }
}
```

### Email Skill 实现

```java
@Component
public class EmailSender {

    private final JavaMailSender mailSender;

    public void send(String to, String subject, String content) {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true); // HTML 格式

        mailSender.send(message);
    }
}
```

### Feishu Skill 实现

```java
@Component
public class FeishuSender {

    @Value("${feishu.app-id}")
    private String appId;

    @Value("${feishu.app-secret}")
    private String appSecret;

    public void send(String feishuUserId, String content) {
        // 1. 获取 tenant_access_token
        String token = getTenantAccessToken();

        // 2. 发送消息
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "receive_id", feishuUserId,
            "msg_type", "text",
            "content", Map.of("text", content)
        );

        restTemplate.postForObject(
            "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=user_id",
            new HttpEntity<>(body, headers),
            String.class
        );
    }
}
```

## Skill 配置

### 环境变量

每个 Skill 通过环境变量配置：

```yaml
# SMS Skill 环境变量
MESSAGEPULSE_CHANNEL_NAME=sms
MESSAGEPULSE_CORE_BASE_URL=http://messagepulse-core:8080
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# 渠道特定配置
SMS_PROVIDER=aliyun
SMS_ACCESS_KEY_ID=xxx
SMS_ACCESS_KEY_SECRET=yyy
```

### application.yml

```yaml
messagepulse:
  channel-name: ${MESSAGEPULSE_CHANNEL_NAME:sms}
  core-base-url: ${MESSAGEPULSE_CORE_BASE_URL:http://localhost:8080}
  topic: messagepulse.messages
  consumer-group: messagepulse.${messagepulse.channel-name}
  heartbeat-interval: 15000

spring:
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: ${messagepulse.consumer-group}
      auto-offset-reset: earliest
      enable-auto-commit: false
```

## 新渠道开发指南

### 开发步骤

1. 创建新的 Spring Boot 项目
2. 添加 Kafka 消费者依赖
3. 实现消息消费者（参考 SMS Skill）
4. 实现渠道发送器
5. 配置环境变量
6. 在 Core 的 `skill_configs` 表中添加配置
7. 部署并启动

### 最小实现

```java
// 1. 消费者
@KafkaListener(topics = "messagepulse.messages", groupId = "messagepulse.new-channel")
public void consume(@Payload Message message, Acknowledgment ack) {
    if (!message.getRouting().getChannels().contains("new-channel")) {
        ack.acknowledge();
        return;
    }

    // 2. 发送
    newChannelSender.send(message);

    // 3. 回执
    receiptProducer.sendReceipt(receipt);

    ack.acknowledge();
}
```

### 开发时间

- 基础 Skill：< 1 天
- 完整 Skill（含重试、监控）：< 2 天

## Skill 管理 API

### Core 端 API

```
GET  /api/v1/skills                         # 列出所有 Skill
GET  /api/v1/skills/{channel}/config        # 获取 Skill 配置
POST /api/v1/skills/{channel}/register      # 注册 Skill 实例
POST /api/v1/skills/{channel}/heartbeat     # 发送心跳
POST /api/v1/skills/{channel}/enable        # 启用 Skill
POST /api/v1/skills/{channel}/disable       # 停用 Skill
DELETE /api/v1/skills/{channel}/instances/{id} # 注销 Skill 实例
```

## 回执生产者

每个 Skill 都需要实现回执生产者，将发送结果写入 `messagepulse.receipts` Topic：

```java
@Service
public class ReceiptProducer {

    private final KafkaTemplate<String, MessageReceipt> kafkaTemplate;

    public void sendReceipt(MessageReceipt receipt) {
        kafkaTemplate.send(
            "messagepulse.receipts",
            receipt.getMessageId(),
            receipt
        );
    }
}
```

## 故障处理

### Skill 崩溃

1. Kafka 检测到消费者心跳超时
2. 触发 Rebalance，其他实例接管分区
3. Core 检测到 Skill 实例心跳超时，标记为 INACTIVE

### 渠道服务商不可用

1. 发送失败，记录错误
2. 发送失败回执（包含 `retryable: true`）
3. Core 根据重试策略决定是否重新发送

### 消息过滤失败

所有 Skill 都从同一 Topic 消费消息，通过应用层过滤。如果过滤逻辑出错，可能导致消息被错误处理或忽略。

**解决方案**：
- 回执包含渠道标识
- Core 可以检测到回执缺失
- 定时任务检查超时消息

## 与其他知识文档的关系

- **系统架构** → `02-architecture.md`：Skill 层在架构中的位置
- **Kafka 设计** → `06-kafka-design.md`：Skill 的 Kafka 消费模式
- **数据库设计** → `04-database-design.md`：skill_configs 和 skill_instances 表
- **API 设计** → `05-api-design.md`：Skill 管理 API
- **部署方案** → `12-deployment.md`：Skill 的 Docker 部署
