# MessagePulse 项目深度解析（面试追问准备）

## 为什么我会思考这些问题？

---

## 一、为什么思考高可用问题？

### Kafka高可用配置的完整性

我构建了3节点Kafka集群，但在配置上有个容易忽略的细节：

**问题：acks=all的陷阱**

很多人认为设置acks=all就能保证消息不丢失，但这有个前提：min.insync.replicas必须大于1。

```
acks=all的含义：等待ISR（同步副本列表）中所有副本确认
min.insync.replicas的含义：ISR中最少需要多少个副本

如果min.insync.replicas=1（默认值）：
- 当某个follower故障落后，ISR可能只剩leader
- 此时acks=all实际上只等待leader确认
- 若leader宕机，消息可能丢失
```

**我的配置：**

```properties
# server.properties
# 副本数
default.replication.factor=3

# 最小同步副本数（关键！）
min.insync.replicas=2

# 生产者配置
acks=all
enable.idempotence=true
max.in.flight.requests.per.connection=5
```

**这个配置的效果：**

| 场景 | 不设置min.insync.replicas | 设置min.insync.replicas=2 |
|------|--------------------------|---------------------------|
| 3节点正常 | ✅ 消息写入3个副本 | ✅ 消息写入3个副本 |
| 1个节点故障 | ⚠️ 可能只写1个副本 | ✅ 至少写2个副本 |
| 2个节点故障 | ❌ 生产者收到异常 | ❌ 生产者收到异常 |

**面试回答要点：**
> "我设置了min.insync.replicas=2，配合acks=all，确保至少两个副本确认才算发送成功。这样即使一个节点故障，数据仍然安全。如果只剩一个节点，生产者会收到异常，可以决定是否重试或降级。"

---

## 二、布隆过滤器的时间窗口处理

### 布隆过滤器的固有限制

布隆过滤器不支持删除操作，如果用于基于时间窗口的去重，会面临：

```
问题1：历史ID累积
- 5分钟内可能有10万个消息ID
- 1小时后累积120万个ID
- 1天后累积2880万个ID
- 误判率持续上升，最终失去去重能力

问题2：重建期间的并发安全
- 定时清空布隆过滤器
- 重建瞬间可能有新的请求漏判
```

### 我的解决方案：双缓冲布隆过滤器

**核心思路：**

使用两个布隆过滤器交替工作，每5分钟切换一次。

```
时间轴：  0 min    5 min    10 min
         |--------|--------|
当前窗口：   BloomFilter-A (活跃)
历史窗口：            BloomFilter-B (备用)
切换时刻：    ↑        ↑
         重建B     重建A
```

**代码实现：**

```java
public class TimeWindowBloomFilter {
    private BloomFilter<String> current;
    private BloomFilter<String> previous;
    private volatile long windowStart;

    // 检查是否重复
    public boolean mightContain(String id) {
        return current.mightContain(id) || previous.mightContain(id);
    }

    // 切换窗口
    private void rotateWindow() {
        synchronized (this) {
            previous = current;
            current = BloomFilter.create(...); // 重建
            windowStart = System.currentTimeMillis();
        }
    }
}
```

**方案优势：**

| 特性 | 优势 |
|------|------|
| 内存可控 | 每个窗口约1.2MB（100万元素×0.01%误判率） |
| 误判率稳定 | 定期重建，始终保持在设计值 |
| 无阻塞 | 切换只需10-50ms，对请求无影响 |

**面试回答要点：**
> "我采用双缓冲方案，两个布隆过滤器交替工作，每5分钟切换一次。检查时同时查当前和历史过滤器，防止切换瞬间的边界问题。这样既保证了去重效果，又控制了内存占用和误判率。"

---

## 三、消息撤回的技术边界

### 现实限制

消息推送有个残酷的现实：**一旦发出去，就收不回来了**

| 发送阶段 | 可撤回性 | 说明 |
|----------|----------|------|
| Kafka队列中 | ✅ 可撤回 | 消息未被消费 |
| 消费者处理中 | ⚠️ 条件可撤回 | 未调用渠道前可取消 |
| 已调用渠道 | ❌ 不可撤回 | 第三方API已调用 |
| 已送达用户 | ❌ 不可撤回 | 短信/邮件已发出 |

### 我的分阶段撤回方案

**设计思路：**

根据消息状态采用不同策略：

```java
public RevokeResult revoke(String messageId) {
    Message msg = repository.findById(messageId);

    switch (msg.getStatus()) {
        case PENDING:
            // 从Kafka删除或标记撤回
            return revokeFromKafka(msg);

        case PROCESSING:
            if (msg.getChannelId() == null) {
                // 通过Redis事件取消
                return revokeFromConsumer(msg);
            } else {
                // 已调用渠道，尝试部分撤回
                return tryChannelRevoke(msg);
            }

        case SENT_TO_CHANNEL:
            // 邮件可发送撤回请求
            // 短信/微信不支持
            return tryChannelRevoke(msg);

        case DELIVERED:
            return RevokeResult.notSupported("已送达，无法撤回");
    }
}
```

**前端展示：**

```javascript
// 根据状态显示撤回按钮
if (status === 'PENDING' || status === 'PROCESSING') {
    showRevokeButton();  // 完全可撤回
} else if (status === 'SENT_TO_CHANNEL' && channel === 'EMAIL') {
    showPartialRevokeButton();  // 部分可撤回
} else {
    hideRevokeButton();  // 不可撤回
}
```

**面试回答要点：**
> "我设计了分阶段撤回机制。消息在Kafka队列中可以完全撤回，消费者处理中但未调用渠道时可以通过Redis事件取消。已发送到渠道的则视渠道能力，比如邮件可以发送撤回请求，但短信和微信就不支持。我在前端会根据状态动态显示撤回按钮，给用户明确预期。"

---

## 四、性能指标的真实含义

### 12000 QPS的组成

这个数字看起来很高，但需要理解它的具体含义：

```
接口层QPS = 12000
    ↓
写入Kafka（异步）
    ↓
消费速度取决于：
- 第三方API响应速度
- 消费者实例数量
- Redis/MySQL性能
```

**压测环境：**

| 组件 | 配置 | 说明 |
|------|------|------|
| 应用服务器 | 4核8G × 2 | 负载均衡 |
| Kafka | 3节点，每节点4核8G | 副本数3 |
| Redis | 单机4核8G | 用于去重 |
| MySQL | 单机4核8G | 兜底存储 |

**20ms P99的构成：**

```
总延迟20ms = 应用层处理(5ms)
           + 网络往返(5ms)
           + Kafka写入(10ms)
```

**去重对性能的影响：**

```
无去重：20ms
+ 布隆过滤器：+1ms（本地内存，几乎无开销）
+ Redis检查：+5ms（网络往返）
= 总计：26ms（仍在可接受范围）
```

**面试回答要点：**
> "12000 QPS是指接口层的吞吐，主业务线程把消息扔进Kafka就返回了。实际消费速度取决于第三方API的响应速度。20ms的P99延迟是接口层的响应时间，包含了应用处理、网络和Kafka写入。如果加上Redis去重，延迟会增加5ms左右，但仍在可接受范围。"

---

## 五、轻量化的真实含义

### 与austin的对比

| 对比项 | austin | MessagePulse |
|--------|--------|--------------|
| Kafka配置 | 默认配置（较高配置） | 精简配置 |
| 定时任务 | 内置复杂调度系统 | 依赖XXL-Job（可选） |
| 多渠道管理 | 内置10+渠道适配器 | 仅实现核心3-4个 |
| 链路追踪 | 自研追踪系统 | 使用SkyWalking（复用） |
| 内存占用 | 16G+ | 约8G（3节点Kafka 6G + 其他2G） |

**"轻量化"体现在：**

1. **代码结构简化**：移除了定时任务、模板管理等复杂模块
2. **核心功能聚焦**：只实现异步解耦、去重、链路追踪
3. **可复用基础设施**：使用公司已有的SkyWalking、XXL-Job

**面试回答要点：**
> "轻量化主要体现在代码结构上，我移除了定时任务、模板管理等复杂模块，聚焦在异步解耦、去重、链路追踪核心功能。同时复用了公司已有的SkyWalking和XXL-Job，避免了重复造轮子。内存占用从austin的16G降到8G左右，更适合中小团队。"

---

## 六、数据可信度

### 0.8% → 0.01% 的来源

**测试方法：**

```
场景：模拟用户快速点击（100次/秒）
持续时间：1小时
总请求数：360,000

无去重：
- 重复发送：2,880次
- 重复率：0.8%

有去重：
- 重复发送：36次（布隆过滤器误判）
- 重复率：0.01%
```

**布隆过滤器参数：**

```
预期元素数：1,000,000
误判率：0.01%
内存占用：约1.2MB
```

**面试回答要点：**
> "测试方法是在1小时内模拟36万次请求，其中包含用户快速点击、网络重试、消费者重复消费等场景。无去重时重复发送2880次，加去重后只重复36次，都是布隆过滤器的误判导致的。0.01%的重复率在可接受范围内。"

---

## 七、总结：我的设计思路

```
                企业消息推送痛点
                     ↓
        ┌────────────┼────────────┐
        ↓            ↓            ↓
    高可用      性能优化      去重问题
        ↓            ↓            ↓
   Kafka完整配置   异步解耦    双缓冲布隆过滤器
   (min.insync.replicas)  削峰填谷   时间窗口处理
        └────────────┼────────────┘
                     ↓
              可观测性（Kafka+Flink）
                     ↓
           分阶段撤回（技术边界清晰）
                     ↓
         最终成果：99%+成功率，8G内存
```

**核心原则：**
1. **严谨性**：技术配置要完整（如min.insync.replicas）
2. **真实性**：明确技术边界（如撤回的限制）
3. **数据支撑**：用测试数据验证设计
4. **适度简化**：轻量化不是功能缺失，而是聚焦核心
