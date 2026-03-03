# Core Engine - Wave 3: 监控指标 + 健康检查

## 完成时间
2026-03-03

## T26 - 监控指标 (metrics/)

| 文件 | 说明 |
|------|------|
| `MessageMetrics.java` | 消息发送计数(by channel/status)、发送延迟直方图、活跃消息数 Gauge |
| `DeduplicationMetrics.java` | 去重命中率(by level L1/L2/L3)、布隆过滤器大小 Gauge、去重检查总数 |
| `KafkaMetrics.java` | Kafka 生产/消费计数(by topic/status)、生产/消费延迟 |
| `SkillMetrics.java` | Skill 活跃实例数 Gauge、发送成功/失败计数(by skill_type)、心跳延迟 |

## T26 - 健康检查 (health/)

| 文件 | 说明 |
|------|------|
| `KafkaHealthIndicator.java` | extends AbstractHealthIndicator，AdminClient 检查集群连接，返回 clusterId/nodeCount |
| `RedisHealthIndicator.java` | extends AbstractHealthIndicator，PING 检查 Redis 连接 |
| `SkillHealthIndicator.java` | extends AbstractHealthIndicator，查询 ACTIVE 状态的 SkillInstance，无活跃实例则 DOWN |

## 编译结果
`mvn compile -Dcheckstyle.skip=true` → BUILD SUCCESS (99 source files)
