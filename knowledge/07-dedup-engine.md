# 去重引擎

## 设计目标

消息去重是 MessagePulse 2.0 的核心能力之一。由于 AI 系统可能因为网络重试、框架重放等原因发送重复消息，去重引擎需要：

1. **低延迟**：去重检查不应显著增加消息处理时间
2. **高准确率**：误判率 < 0.01%
3. **低资源消耗**：内存占用可控
4. **时间窗口**：在一定时间窗口内去重

## 三级去重架构

MessagePulse 2.0 采用三级去重机制，每一级在性能和准确率上逐步递进：

```
消息到达
   ↓
┌──────────────────────┐
│ 第一级：布隆过滤器    │  ← 内存操作，O(1)
│ 快速过滤，几乎零成本  │
│ 可能有误判（假阳性）  │
└──────────┬───────────┘
           │ "可能存在"
           ↓
┌──────────────────────┐
│ 第二级：Redis 精确去重 │  ← 网络操作，O(1)
│ 高并发，精确检查       │
│ 带过期时间（10 分钟）  │
└──────────┬───────────┘
           │ "确认重复" 或 "新消息"
           ↓
┌──────────────────────┐
│ 第三级：MySQL 兜底    │  ← 磁盘操作
│ 唯一约束保证一致性    │
│ 最终一致性保障        │
└──────────────────────┘
```

### 第一级：布隆过滤器（BloomFilter）

**作用**：快速过滤明确不重复的消息（减少 Redis 调用）。

**特点**：
- 内存操作，速度极快
- 可能有假阳性（说"可能存在"但实际不存在）
- 不会有假阴性（说"不存在"则一定不存在）
- 不可删除元素

**配置参数**：

```yaml
messagepulse:
  dedup:
    bloom-filter:
      expected-insertions: 1000000    # 预期元素数（5分钟窗口）
      false-probability: 0.0001       # 误判率 0.01%
      window-size-seconds: 300        # 时间窗口 5分钟
```

**内存计算**：

```
位图大小 ≈ -n * ln(p) / (ln(2))^2
         ≈ -1000000 * ln(0.0001) / (ln(2))^2
         ≈ 350 KB

哈希函数数量 ≈ -(ln(p) / ln(2))
             ≈ -(ln(0.0001) / ln(2))
             ≈ 13 个

总内存（双缓冲）≈ 700 KB
```

**实现代码**：

```java
@Service
public class BloomFilterDedupService {

    private BloomFilter<String> currentFilter;
    private BloomFilter<String> previousFilter;

    @Value("${messagepulse.dedup.bloom-filter.expected-insertions}")
    private int expectedInsertions;

    @Value("${messagepulse.dedup.bloom-filter.false-probability}")
    private double falseProbability;

    @PostConstruct
    public void init() {
        this.currentFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            expectedInsertions,
            falseProbability
        );
        this.previousFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            expectedInsertions,
            falseProbability
        );
    }

    /**
     * 检查消息是否可能存在（可能有假阳性）
     */
    public boolean mightContain(String messageId) {
        return currentFilter.mightContain(messageId)
            || previousFilter.mightContain(messageId);
    }

    /**
     * 添加消息到布隆过滤器
     */
    public void put(String messageId) {
        currentFilter.put(messageId);
    }
}
```

### 第二级：Redis 精确去重

**作用**：对布隆过滤器返回"可能存在"的消息进行精确检查。

**特点**：
- 精确检查，无误判
- 带过期时间，自动清理
- 支持高并发

**实现代码**：

```java
@Service
public class RedisDedupService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String DEDUP_KEY_PREFIX = "msg:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofMinutes(10);

    /**
     * 检查消息是否已存在
     */
    public boolean exists(String messageId) {
        String key = DEDUP_KEY_PREFIX + messageId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 标记消息为已处理
     */
    public void markAsProcessed(String messageId) {
        String key = DEDUP_KEY_PREFIX + messageId;
        redisTemplate.opsForValue().set(key, "1", DEDUP_TTL);
    }

    /**
     * 原子检查并标记（使用 SETNX）
     */
    public boolean checkAndMark(String messageId) {
        String key = DEDUP_KEY_PREFIX + messageId;
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(key, "1", DEDUP_TTL);
        // setIfAbsent 返回 true 表示 key 不存在（新消息）
        // 返回 false 表示 key 已存在（重复消息）
        return Boolean.FALSE.equals(result);
    }
}
```

### 第三级：MySQL 兜底

**作用**：通过唯一约束保证最终一致性。

**特点**：
- 磁盘操作，速度最慢
- 唯一约束保证绝对不重复
- 作为最终兜底

**实现方式**：

```java
// messages 表的 message_id 是主键，天然唯一
// 插入时如果 message_id 已存在，会抛出 DuplicateKeyException

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    // message_id 作为主键，JPA 的 save() 方法：
    // - 新消息：INSERT
    // - 重复消息：抛出异常（被捕获处理）
}
```

## 双缓冲布隆过滤器

### 设计理念

布隆过滤器不支持删除元素，随着时间推移会积累越来越多的误判。双缓冲机制通过定期轮换来解决这个问题。

### 时间窗口

```
时间轴：  0 min    5 min    10 min   15 min
         |--------|--------|--------|
窗口 1：  BloomFilter-A (活跃)
窗口 2：             BloomFilter-B (活跃)
窗口 3：                      BloomFilter-A (活跃, 重建)
切换时刻：    ↑        ↑        ↑
          A活跃     B活跃     A重建并活跃
```

### 实现代码

```java
@Component
public class DoubleBufferBloomFilter {

    private volatile BloomFilter<String> activeFilter;
    private volatile BloomFilter<String> backupFilter;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Value("${messagepulse.dedup.bloom-filter.expected-insertions}")
    private int expectedInsertions;

    @Value("${messagepulse.dedup.bloom-filter.false-probability}")
    private double falseProbability;

    @PostConstruct
    public void init() {
        activeFilter = createFilter();
        backupFilter = createFilter();
    }

    private BloomFilter<String> createFilter() {
        return BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            expectedInsertions,
            falseProbability
        );
    }

    /**
     * 检查消息是否可能存在
     */
    public boolean mightContain(String messageId) {
        lock.readLock().lock();
        try {
            return activeFilter.mightContain(messageId)
                || backupFilter.mightContain(messageId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 添加消息
     */
    public void put(String messageId) {
        lock.readLock().lock();
        try {
            activeFilter.put(messageId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 轮换过滤器（定时调用）
     */
    @Scheduled(fixedRateString = "${messagepulse.dedup.bloom-filter.window-size-seconds}000")
    public void rotate() {
        lock.writeLock().lock();
        try {
            // 备用 → 重建为新的空过滤器
            // 活跃 → 变为备用
            // 新建 → 变为活跃
            BloomFilter<String> newFilter = createFilter();
            backupFilter = activeFilter;
            activeFilter = newFilter;

            log.info("BloomFilter rotated. Active filter reset.");
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

### 查询流程

```
1. 收到消息 messageId
   ↓
2. 检查 activeFilter.mightContain(messageId)
   ↓ 如果不存在
3. 检查 backupFilter.mightContain(messageId)
   ↓ 如果不存在
4. 确认为新消息，添加到 activeFilter
   ↓ 如果存在（可能重复）
5. 进入第二级 Redis 精确检查
```

## 完整去重流程

### 代码实现

```java
@Service
public class DedupService {

    private final DoubleBufferBloomFilter bloomFilter;
    private final RedisDedupService redisDedupService;
    private final MeterRegistry meterRegistry;

    /**
     * 检查消息是否重复
     *
     * @return true 如果是重复消息
     */
    public boolean isDuplicate(String messageId) {
        // 第一级：布隆过滤器
        if (!bloomFilter.mightContain(messageId)) {
            // 布隆过滤器说不存在，则一定不存在
            bloomFilter.put(messageId);
            recordDedupMetric("bloom", false);
            return false;
        }

        // 布隆过滤器说可能存在，需要 Redis 精确检查
        recordDedupMetric("bloom", true);

        // 第二级：Redis 精确去重
        if (redisDedupService.exists(messageId)) {
            // Redis 确认存在，是重复消息
            recordDedupMetric("redis", true);
            return true;
        }

        // Redis 不存在，不是重复消息
        // 添加到布隆过滤器
        bloomFilter.put(messageId);
        recordDedupMetric("redis", false);
        return false;
    }

    /**
     * 标记消息为已处理
     */
    public void markAsProcessed(String messageId) {
        bloomFilter.put(messageId);
        redisDedupService.markAsProcessed(messageId);
        // 第三级：MySQL 通过消息表的主键唯一约束保证
    }

    private void recordDedupMetric(String level, boolean hit) {
        Counter.builder("messagepulse.deduplication")
            .tag("level", level)
            .tag("result", hit ? "hit" : "miss")
            .register(meterRegistry)
            .increment();
    }
}
```

### 流程图

```
消息到达（messageId）
   │
   ▼
bloomFilter.mightContain(messageId)?
   │
   ├── 不存在 ──→ 新消息，添加到 bloomFilter → 继续处理
   │
   └── 可能存在 ──→ Redis 精确检查
                      │
                      ├── Redis 存在 ──→ 重复消息，丢弃
                      │
                      └── Redis 不存在 ──→ 新消息，添加到 bloomFilter → 继续处理
                                            │
                                            ▼
                                      消息处理完成后
                                            │
                                            ▼
                                      markAsProcessed()
                                      1. bloomFilter.put()
                                      2. Redis SET (TTL 10min)
                                      3. MySQL INSERT (唯一约束)
```

## 性能分析

### 各级去重的性能特征

| 级别 | 操作类型 | 延迟 | 吞吐量 | 准确率 | 资源消耗 |
|------|---------|------|--------|--------|---------|
| 布隆过滤器 | 内存 | < 1us | 极高 | 99.99% | ~700 KB |
| Redis | 网络 | < 1ms | 高 | 100% | 按消息数 |
| MySQL | 磁盘 | 1-5ms | 中等 | 100% | 按消息数 |

### 去重率估算

在正常负载下：
- 布隆过滤器过滤掉 ~99% 的非重复消息（减少 Redis 调用）
- Redis 处理剩余 ~1% 的可疑消息
- MySQL 作为最终兜底，极少使用

### 内存占用

```
双缓冲布隆过滤器：~700 KB
Redis 去重 Key：~10 MB（每分钟 10万消息，每 Key ~100 Bytes）
总内存：< 15 MB
```

## 监控指标

```java
// 去重命中率
messagepulse_deduplication_total{level="bloom", result="hit"}
messagepulse_deduplication_total{level="bloom", result="miss"}
messagepulse_deduplication_total{level="redis", result="hit"}
messagepulse_deduplication_total{level="redis", result="miss"}

// 布隆过滤器大小（近似）
messagepulse_bloom_filter_approximate_element_count

// 布隆过滤器误判率（实际）
messagepulse_bloom_filter_false_positive_rate
```

## 设计决策

### 决策 1：为什么使用三级而不是单级？

**权衡**：
- 纯布隆过滤器：有误判，可能导致消息丢失
- 纯 Redis：性能够用，但每次都需要网络调用
- 纯 MySQL：性能不足，无法支撑高 QPS
- 三级组合：布隆过滤器过滤大部分流量，Redis 精确检查，MySQL 兜底

### 决策 2：为什么使用双缓冲？

**问题**：布隆过滤器不支持删除元素，元素越多，误判率越高。
**方案**：定期轮换，旧过滤器作为备用，新过滤器从空开始。
**代价**：轮换瞬间，最近的消息可能无法被旧过滤器识别（但 Redis 兜底）。

### 决策 3：时间窗口为什么选 5 分钟？

**考虑**：
- 太短：频繁轮换，增加 Redis 调用
- 太长：过滤器元素多，误判率上升
- 5 分钟是平衡点，预期 100 万元素，误判率 0.01%

## 与其他知识文档的关系

- **系统架构** → `02-architecture.md`：去重引擎在处理层的位置
- **技术栈详解** → `03-tech-stack.md`：Guava BloomFilter 的选型
- **Kafka 设计** → `06-kafka-design.md`：消息消费时的去重检查
- **监控设计** → `10-monitoring.md`：去重引擎的监控指标
