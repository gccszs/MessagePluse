#!/bin/bash

# 等待服务启动并健康的脚本

echo "Waiting for services to be ready..."

# 等待 MySQL
echo "Waiting for MySQL..."
max_attempts=30
attempt=0
while [ $attempt -lt $max_attempts ]; do
  if docker exec messagepulse-mysql-1 mysqladmin ping -h localhost --silent 2>/dev/null; then
    echo "MySQL is ready!"
    break
  fi
  attempt=$((attempt+1))
  echo "Attempt $attempt/$max_attempts: MySQL not ready yet..."
  sleep 2
done

if [ $attempt -eq $max_attempts ]; then
  echo "ERROR: MySQL failed to start"
  exit 1
fi

# 等待 Redis
echo "Waiting for Redis..."
attempt=0
while [ $attempt -lt $max_attempts ]; do
  if docker exec messagepulse-redis-1 redis-cli ping 2>/dev/null | grep -q PONG; then
    echo "Redis is ready!"
    break
  fi
  attempt=$((attempt+1))
  echo "Attempt $attempt/$max_attempts: Redis not ready yet..."
  sleep 1
done

if [ $attempt -eq $max_attempts ]; then
  echo "ERROR: Redis failed to start"
  exit 1
fi

# 等待 Kafka
echo "Waiting for Kafka..."
attempt=0
while [ $attempt -lt $max_attempts ]; do
  if docker exec messagepulse-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null >/dev/null; then
    echo "Kafka is ready!"
    break
  fi
  attempt=$((attempt+1))
  echo "Attempt $attempt/$max_attempts: Kafka not ready yet..."
  sleep 2
done

if [ $attempt -eq $max_attempts ]; then
  echo "ERROR: Kafka failed to start"
  exit 1
fi

# 等待 MessagePulse Core
echo "Waiting for MessagePulse Core..."
attempt=0
while [ $attempt -lt $max_attempts ]; do
  if curl -f http://localhost:8080/actuator/health 2>/dev/null >/dev/null; then
    echo "MessagePulse Core is ready!"
    break
  fi
  attempt=$((attempt+1))
  echo "Attempt $attempt/$max_attempts: MessagePulse Core not ready yet..."
  sleep 2
done

if [ $attempt -eq $max_attempts ]; then
  echo "WARNING: MessagePulse Core might not be ready, continuing..."
fi

echo ""
echo "✅ All services are ready!"
echo ""
echo "Service endpoints:"
echo "  - MessagePulse Core: http://localhost:8080"
echo "  - MySQL: localhost:3306"
echo "  - Redis: localhost:6379"
echo "  - Kafka: localhost:9092"
echo "  - Grafana: http://localhost:3000"
echo "  - Prometheus: http://localhost:9090"
