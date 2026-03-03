# Wave 2: Prometheus/Grafana 监控配置

**执行时间**: 2026-03-03
**执行者**: infrastructure agent
**任务**: Task #6 - Prometheus/Grafana 监控配置

---

## 1. 配置文件创建

### 1.1 Prometheus 配置
**文件**: `prometheus/prometheus.yml`

配置内容:
- global scrape_interval: 15s
- global evaluation_interval: 15s
- scrape_configs:
  - job_name: messagepulse-core
  - metrics_path: /actuator/prometheus
  - target: messagepulse-core:8080

### 1.2 Grafana Dashboard
**文件**: `grafana/dashboards/messagepulse-dashboard.json`

包含 6 个监控面板:
1. **系统健康概览** - 服务状态实时监控
2. **消息发送速率** - rate(messagepulse_messages_sent_total[5m])
3. **消息状态分布** - 按状态分组的饼图
4. **API 响应时间** - p50/p95/p99 分位数
5. **Kafka 消费延迟** - 按 topic 分组的延迟监控
6. **JVM 内存使用** - Heap/Non-Heap 内存使用情况

### 1.3 Grafana Provisioning
**文件**:
- `grafana/dashboards/dashboard.yml` - Dashboard 自动加载配置
- `grafana/provisioning/datasources/prometheus.yml` - Prometheus 数据源配置

---

## 2. Docker Compose 更新

### 2.1 新增服务

#### Prometheus 服务
```yaml
prometheus:
  image: prom/prometheus:latest
  container_name: messagepulse-prometheus
  ports: 9090:9090
  volumes:
    - prometheus/prometheus.yml (配置文件)
    - prometheus-data (数据持久化)
  depends_on: messagepulse-core
  networks: messagepulse-network
```

#### Grafana 服务
```yaml
grafana:
  image: grafana/grafana:latest
  container_name: messagepulse-grafana
  ports: 3000:3000
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
  volumes:
    - grafana/dashboards (Dashboard 配置)
    - grafana/provisioning/datasources (数据源配置)
    - grafana-data (数据持久化)
  depends_on: prometheus
  networks: messagepulse-network
```

### 2.2 新增 Volumes
- prometheus-data
- grafana-data

---

## 3. 监控指标说明

### 3.1 应用指标
- `messagepulse_messages_sent_total` - 消息发送总数
- `messagepulse_messages_total` - 按状态分组的消息总数
- `http_server_requests_seconds_bucket` - HTTP 请求响应时间直方图

### 3.2 Kafka 指标
- `kafka_consumer_fetch_manager_records_lag` - 消费者延迟

### 3.3 JVM 指标
- `jvm_memory_used_bytes` - JVM 内存使用量
- `jvm_memory_max_bytes` - JVM 最大内存

---

## 4. 访问方式

- **Prometheus UI**: http://localhost:9090
- **Grafana UI**: http://localhost:3000
  - 默认用户名: admin
  - 默认密码: admin

---

## 5. 文件清单

```
prometheus/
  └── prometheus.yml

grafana/
  ├── dashboards/
  │   ├── dashboard.yml
  │   └── messagepulse-dashboard.json
  └── provisioning/
      └── datasources/
          └── prometheus.yml

docker-compose.yml (已更新)
```

---

## 6. 验证步骤

1. 启动服务: `docker-compose up -d prometheus grafana`
2. 访问 Prometheus: http://localhost:9090/targets (检查 target 状态)
3. 访问 Grafana: http://localhost:3000 (自动加载 Dashboard)
4. 验证数据源连接正常
5. 验证 Dashboard 面板数据展示

---

**状态**: ✅ 完成
**下一步**: 等待 QA 代码审查 (Task #7)
