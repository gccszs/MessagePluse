# Step 02 - DTO 创建记录

## 创建时间
2026-03-02

## 文件清单

### 请求 DTO (dto/request/)
| 文件 | 说明 |
|------|------|
| `SendMessageRequest.java` | 发送消息请求，包含收件人、内容、路由配置 |
| `CreateApiKeyRequest.java` | 创建API Key请求，指定租户、权限范围、限流 |
| `RevokeMessageRequest.java` | 撤回消息请求 |

### 响应 DTO (dto/response/)
| 文件 | 说明 |
|------|------|
| `SendMessageResponse.java` | 发送消息响应，返回messageId和状态 |
| `MessageStatusResponse.java` | 消息状态响应，包含投递状态和回执 |
| `ApiKeyResponse.java` | API Key响应，返回key和配置信息 |
| `SkillStatusResponse.java` | Skill状态响应，包含活跃实例数 |

### 通用 DTO (dto/)
| 文件 | 说明 |
|------|------|
| `MessageContent.java` | 消息内容（subject, body, metadata, attachments） |
| `RoutingConfig.java` | 路由配置（mode, strategy, consistency） |
| `RecipientInfo.java` | 收件人信息（多渠道地址） |

### 事件 (event/)
| 文件 | 说明 |
|------|------|
| `MessageEvent.java` | Kafka消息事件体 |
| `MessageReceipt.java` | 渠道回执事件 |

## 设计说明
- 请求DTO使用 Jakarta Validation 注解校验
- 所有DTO使用 Lombok @Data/@Builder
- RoutingConfig 组合了路由模式、策略和一致性策略
