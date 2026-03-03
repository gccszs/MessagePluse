# 部署方案

## 部署架构

MessagePulse 2.0 支持多种部署方式，从本地开发到生产环境。

```
┌─────────────────────────────────────────────────────────┐
│  部署方式                                                │
├─────────────────────────────────────────────────────────┤
│  1. Docker Compose（本地开发、测试）                     │
│     - 一键启动所有服务                                   │
│     - 适合开发和测试环境                                 │
│                                                         │
│  2. Kubernetes（生产环境）                               │
│     - 自动扩缩容                                         │
│     - 高可用部署                                         │
│     - 滚动更新                                           │
│                                                         │
│  3. 传统部署（物理机/虚拟机）                            │
│     - 手动部署各组件                                     │
│     - 适合特殊环境                                       │
└─────────────────────────────────────────────────────────┘
```

## Docker Compose 部署

### 完整配置

```yaml
version: '3.8'

services:
  # ==================== 基础设施 ====================

  # Zookeeper
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper_data:/var/lib/zookeeper/data
      - zookeeper_logs:/var/lib/zookeeper/log

  # Kafka 集群（3 节点）
  kafka-1:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_LOG_RETENTION_HOURS: 168
      KAFKA_COMPRESSION_TYPE: lz4
    volumes:
      - kafka1_data:/var/lib/kafka/data

  kafka-2:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9093:9092"
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
    volumes:
      - kafka2_data:/var/lib/kafka/data

  kafka-3:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9094:9092"
    environment:
      KAFKA_BROKER_ID: 3
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-3:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
    volumes:
      - kafka3_data:/var/lib/kafka/data

  # MySQL
  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: messagepulse
    volumes:
      - mysql_data:/var/lib/mysql
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql
    command: --default-authentication-plugin=mysql_native_password

  # Redis
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes

  # ==================== MessagePulse Core ====================

  messagepulse-core:
    image: messagepulse/core:2.0.0
    build:
      context: ./messagepulse-core
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/messagepulse
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=password
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
    depends_on:
      - mysql
      - kafka-1
      - kafka-2
      - kafka-3
      - redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # ==================== Channel Skills ====================

  sms-skill:
    image: messagepulse/sms-skill:2.0.0
    build:
      context: ./skills/sms-skill
      dockerfile: Dockerfile
    environment:
      - MESSAGEPULSE_CHANNEL_NAME=sms
      - MESSAGEPULSE_CORE_BASE_URL=http://messagepulse-core:8080
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
      - SMS_PROVIDER=aliyun
      - SMS_ACCESS_KEY_ID=${SMS_ACCESS_KEY_ID}
      - SMS_ACCESS_KEY_SECRET=${SMS_ACCESS_KEY_SECRET}
    depends_on:
      - messagepulse-core
      - kafka-1

  email-skill:
    image: messagepulse/email-skill:2.0.0
    build:
      context: ./skills/email-skill
      dockerfile: Dockerfile
    environment:
      - MESSAGEPULSE_CHANNEL_NAME=email
      - MESSAGEPULSE_CORE_BASE_URL=http://messagepulse-core:8080
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
      - SPRING_MAIL_HOST=${SMTP_HOST}
      - SPRING_MAIL_PORT=${SMTP_PORT}
      - SPRING_MAIL_USERNAME=${SMTP_USERNAME}
      - SPRING_MAIL_PASSWORD=${SMTP_PASSWORD}
    depends_on:
      - messagepulse-core
      - kafka-1

  feishu-skill:
    image: messagepulse/feishu-skill:2.0.0
    build:
      context: ./skills/feishu-skill
      dockerfile: Dockerfile
    environment:
      - MESSAGEPULSE_CHANNEL_NAME=feishu
      - MESSAGEPULSE_CORE_BASE_URL=http://messagepulse-core:8080
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
      - FEISHU_APP_ID=${FEISHU_APP_ID}
      - FEISHU_APP_SECRET=${FEISHU_APP_SECRET}
    depends_on:
      - messagepulse-core
      - kafka-1

  # ==================== 监控 ====================

  prometheus:
    image: prom/prometheus:v2.47.0
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./monitoring/rules:/etc/prometheus/rules
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'

  grafana:
    image: grafana/grafana:10.1.0
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_INSTALL_PLUGINS=redis-datasource
    volumes:
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources
      - grafana_data:/var/lib/grafana
    depends_on:
      - prometheus

  alertmanager:
    image: prom/alertmanager:v0.26.0
    ports:
      - "9093:9093"
    volumes:
      - ./monitoring/alertmanager.yml:/etc/alertmanager/alertmanager.yml
      - alertmanager_data:/alertmanager

  # ==================== 可选组件 ====================

  # XXL-Job（可选）
  # xxl-job-admin:
  #   image: xuxueli/xxl-job-admin:2.4.0
  #   ports:
  #     - "8081:8080"
  #   environment:
  #     - PARAMS="--spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job --spring.datasource.username=root --spring.datasource.password=password"
  #   depends_on:
  #     - mysql

volumes:
  zookeeper_data:
  zookeeper_logs:
  kafka1_data:
  kafka2_data:
  kafka3_data:
  mysql_data:
  redis_data:
  prometheus_data:
  grafana_data:
  alertmanager_data:
```

### 环境变量配置

创建 `.env` 文件：

```bash
# SMS 配置
SMS_ACCESS_KEY_ID=your_aliyun_access_key_id
SMS_ACCESS_KEY_SECRET=your_aliyun_access_key_secret

# Email 配置
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=noreply@example.com
SMTP_PASSWORD=your_smtp_password

# 飞书配置
FEISHU_APP_ID=your_feishu_app_id
FEISHU_APP_SECRET=your_feishu_app_secret
```

### 启动命令

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f messagepulse-core

# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

## Dockerfile

### Core Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制 JAR 文件
COPY target/messagepulse-core-2.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

### Skill Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/sms-skill-2.0.0.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

## 数据库初始化

### init.sql

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS messagepulse
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE messagepulse;

-- 创建表（参考 04-database-design.md）
-- messages 表
CREATE TABLE messages (
    message_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    content JSON NOT NULL,
    routing JSON NOT NULL,
    priority VARCHAR(20) NOT NULL,
    recipient JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    expires_at TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 其他表...（省略）

-- 初始化 Skill 配置
INSERT INTO skill_configs (channel_name, display_name, consumer_group, topic, filter_expression, enabled)
VALUES
('sms', '短信', 'messagepulse.sms', 'messagepulse.messages', 'channels contains "sms"', true),
('email', '邮件', 'messagepulse.email', 'messagepulse.messages', 'channels contains "email"', true),
('feishu', '飞书', 'messagepulse.feishu', 'messagepulse.messages', 'channels contains "feishu"', true);
```

## CI/CD 配置

### GitHub Actions

```yaml
# .github/workflows/build.yml
name: Build and Deploy

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn clean package -DskipTests

      - name: Run tests
        run: mvn test

      - name: Build Docker images
        run: |
          docker build -t messagepulse/core:${{ github.sha }} ./messagepulse-core
          docker build -t messagepulse/sms-skill:${{ github.sha }} ./skills/sms-skill
          docker build -t messagepulse/email-skill:${{ github.sha }} ./skills/email-skill
          docker build -t messagepulse/feishu-skill:${{ github.sha }} ./skills/feishu-skill

      - name: Push to Docker Hub
        if: github.ref == 'refs/heads/main'
        run: |
          echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
          docker push messagepulse/core:${{ github.sha }}
          docker push messagepulse/sms-skill:${{ github.sha }}
          docker push messagepulse/email-skill:${{ github.sha }}
          docker push messagepulse/feishu-skill:${{ github.sha }}
```

## 性能指标

### 资源需求

| 组件 | CPU | 内存 | 磁盘 | 说明 |
|------|-----|------|------|------|
| MessagePulse Core | 2 核 | 4 GB | 10 GB | 核心服务 |
| Kafka (3 节点) | 2 核 × 3 | 4 GB × 3 | 100 GB × 3 | 消息队列 |
| MySQL | 2 核 | 4 GB | 50 GB | 数据库 |
| Redis | 1 核 | 2 GB | 10 GB | 缓存 |
| SMS Skill | 1 核 | 2 GB | 5 GB | 短信渠道 |
| Email Skill | 1 核 | 2 GB | 5 GB | 邮件渠道 |
| Feishu Skill | 1 核 | 2 GB | 5 GB | 飞书渠道 |
| Prometheus | 1 核 | 2 GB | 20 GB | 监控 |
| Grafana | 1 核 | 1 GB | 5 GB | 可视化 |
| **总计** | **16 核** | **31 GB** | **315 GB** | 完整部署 |

### 性能目标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 接口层 QPS | 12000+ | 峰值吞吐 |
| 接口延迟 P99 | < 20ms | 接口响应时间 |
| 消息重复率 | < 0.01% | 去重后 |
| 消息成功率 | > 99% | 所有渠道综合 |
| 系统可用性 | > 99.9% | 年停机时间 < 8.76 小时 |

## 运维脚本

### 启动脚本

```bash
#!/bin/bash
# start.sh

echo "Starting MessagePulse 2.0..."

# 检查 Docker 和 Docker Compose
if ! command -v docker &> /dev/null; then
    echo "Docker is not installed"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "Docker Compose is not installed"
    exit 1
fi

# 启动服务
docker-compose up -d

# 等待服务启动
echo "Waiting for services to start..."
sleep 30

# 健康检查
echo "Checking service health..."
curl -f http://localhost:8080/actuator/health || echo "Core service not ready"
curl -f http://localhost:9090/-/healthy || echo "Prometheus not ready"
curl -f http://localhost:3000/api/health || echo "Grafana not ready"

echo "MessagePulse 2.0 started successfully!"
echo "Core API: http://localhost:8080"
echo "Prometheus: http://localhost:9090"
echo "Grafana: http://localhost:3000 (admin/admin)"
```

### 停止脚本

```bash
#!/bin/bash
# stop.sh

echo "Stopping MessagePulse 2.0..."
docker-compose down
echo "MessagePulse 2.0 stopped"
```

### 备份脚本

```bash
#!/bin/bash
# backup.sh

BACKUP_DIR="/backup/messagepulse"
DATE=$(date +%Y%m%d_%H%M%S)

echo "Starting backup..."

# 备份 MySQL
docker exec messagepulse-mysql mysqldump -u root -ppassword messagepulse > "$BACKUP_DIR/mysql_$DATE.sql"

# 备份 Redis
docker exec messagepulse-redis redis-cli SAVE
docker cp messagepulse-redis:/data/dump.rdb "$BACKUP_DIR/redis_$DATE.rdb"

# 压缩备份
tar -czf "$BACKUP_DIR/backup_$DATE.tar.gz" "$BACKUP_DIR/mysql_$DATE.sql" "$BACKUP_DIR/redis_$DATE.rdb"

# 删除临时文件
rm "$BACKUP_DIR/mysql_$DATE.sql" "$BACKUP_DIR/redis_$DATE.rdb"

echo "Backup completed: $BACKUP_DIR/backup_$DATE.tar.gz"
```

## 与其他知识文档的关系

- **系统架构** → `02-architecture.md`：部署架构对应系统架构
- **技术栈详解** → `03-tech-stack.md`：部署的技术组件
- **Kafka 设计** → `06-kafka-design.md`：Kafka 集群部署
- **Channel Skills** → `09-channel-skills.md`：Skill 的部署配置
- **监控设计** → `10-monitoring.md`：监控服务的部署
