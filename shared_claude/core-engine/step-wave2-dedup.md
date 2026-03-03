# Core Engine - Wave 2: 去重 + 撤回 + 一致性引擎

## 完成时间
2026-03-03

## T16 - 三级去重引擎

### 创建的文件 (engine/dedup/)
| 文件 | 说明 |
|------|------|
| `DeduplicationEngine.java` | 接口，`boolean isDuplicate(String messageId, String tenantId)` |
| `ThreeLevelDeduplicationEngine.java` | @Component @Primary，三级去重：L1 Guava Cache → L2 Redis SET(Lua脚本) → L3 布隆过滤器 |
| `TimeWindowBloomFilter.java` | 双缓冲布隆过滤器（currentWindow + previousWindow），使用 Guava BloomFilter |
| `BloomFilterRotationTask.java` | @Scheduled 每小时轮换布隆过滤器窗口 |

### 配置文件 (config/)
| 文件 | 说明 |
|------|------|
| `RedisConfig.java` | RedisTemplate/StringRedisTemplate bean 配置 |
| `DeduplicationConfig.java` | @ConfigurationProperties，去重参数（cache大小、Redis TTL、布隆过滤器参数） |

### Lua 脚本
| 文件 | 说明 |
|------|------|
| `scripts/dedup-check.lua` | Redis 原子性 SISMEMBER + SADD + EXPIRE |

## T17 - 撤回引擎

### 创建的文件 (engine/revoke/)
| 文件 | 说明 |
|------|------|
| `RevokeEngine.java` | 接口，`void revoke(String messageId, String tenantId)` |
| `DefaultRevokeEngine.java` | @Component，分阶段撤回：PENDING/ROUTING直接撤→SENDING通知撤→SENT标记已撤回 |

### Controller
| 文件 | 说明 |
|------|------|
| `RevokeController.java` | POST /api/v1/messages/{messageId}/revoke，X-Tenant-Id 头验证 |

## T18 - 一致性引擎

### 创建的文件 (engine/consistency/)
| 文件 | 说明 |
|------|------|
| `ConsistencyEngine.java` | 接口，evaluate() + getRequiredChannels() |
| `EventualConsistencyStrategy.java` | 最终一致性：任一渠道成功即可 |
| `AtLeastOneStrategy.java` | 至少一个成功 |
| `AllOrNoneStrategy.java` | 全部成功或全部失败 |
| `PriorityOrderStrategy.java` | 按优先级顺序，首个成功即可 |

## 修改的文件
无（ErrorCode 在 Wave 1 已添加所需错误码）

## 编译结果
`mvn compile -Dcheckstyle.skip=true` → BUILD SUCCESS (92 source files)
