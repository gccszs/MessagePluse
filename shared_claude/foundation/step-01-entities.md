# Step 01 - 实体类创建记录

## 创建时间
2026-03-02

## 文件清单

| 文件 | 对应表 | 说明 |
|------|--------|------|
| `entity/Message.java` | messages | 消息主表，UUID主键，JSON字段存储content和routingConfig |
| `entity/MessageState.java` | message_states | 消息状态记录，自增主键 |
| `entity/ApiKey.java` | api_keys | API密钥表，支持keyHash索引 |
| `entity/SkillConfig.java` | skill_configs | Skill配置表，JSON存储config |
| `entity/SkillInstance.java` | skill_instances | Skill实例表，记录心跳和状态 |
| `entity/BillingRecord.java` | billing_records | 计费记录表，BigDecimal存储cost |
| `entity/MessageTemplate.java` | message_templates | 消息模板表，text类型content |

## 设计说明
- 所有实体使用 Lombok (@Getter/@Setter/@Builder) 减少样板代码
- JSON 字段使用 `@JdbcTypeCode(SqlTypes.JSON)` 注解
- 时间字段自动填充：`@PrePersist` / `@PreUpdate`
- 索引通过 `@Table(indexes = ...)` 声明
- 状态枚举字段使用 `@Enumerated(EnumType.STRING)` 存储可读字符串
