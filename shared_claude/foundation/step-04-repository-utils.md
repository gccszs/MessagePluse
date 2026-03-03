# Step 04 - Repository 和工具类创建记录

## 创建时间
2026-03-02

## Repository 接口清单 (repository/)

| 文件 | 实体 | 自定义查询方法 |
|------|------|----------------|
| `MessageRepository.java` | Message | findByMessageId, findByTenantIdAndStatus, findByStatusAndCreatedAtBefore, existsByMessageId |
| `MessageStateRepository.java` | MessageState | findByMessageIdOrderByCreatedAtDesc, findByMessageIdAndChannelType |
| `ApiKeyRepository.java` | ApiKey | findByKeyHash, findByTenantIdAndIsActive |
| `SkillConfigRepository.java` | SkillConfig | findByTenantIdAndChannelTypeAndIsEnabled, findByTenantIdAndIsEnabled |
| `SkillInstanceRepository.java` | SkillInstance | findByInstanceId, findBySkillConfigIdAndStatus, findByStatus |
| `BillingRecordRepository.java` | BillingRecord | findByTenantIdAndBillingTimeBetween, findByMessageId |
| `MessageTemplateRepository.java` | MessageTemplate | findByTenantIdAndTemplateNameAndChannelType, findByTenantIdAndChannelType |

## 工具类清单 (util/)

| 文件 | 说明 |
|------|------|
| `MessageIdGenerator.java` | 雪花算法ID生成器（单例模式，基于时间戳+workerId+序列号） |
| `ApiKeyGenerator.java` | API Key生成器（SecureRandom + Base64，SHA-256哈希） |
| `JsonUtils.java` | JSON工具类（基于Jackson，支持Java 8时间类型） |

## 设计说明
- 所有 Repository 继承 JpaRepository，使用 Spring Data JPA 方法命名规范
- MessageIdGenerator 使用雪花算法，支持分布式ID生成
- ApiKeyGenerator 生成格式：`mp_` + Base64编码随机字节
- JsonUtils 配置了 JavaTimeModule 和合理的序列化选项

## 编译验证
✅ 执行 `mvn compile` 编译通过
