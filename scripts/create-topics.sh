#!/bin/bash
# MessagePulse - Kafka Topic Creation Script
# Creates all required Kafka topics with specified partitions and replication factor

set -e

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-localhost:9092}"
MAX_RETRIES=30
RETRY_INTERVAL=5

echo "[create-topics] Waiting for Kafka at ${BOOTSTRAP_SERVER}..."

for i in $(seq 1 $MAX_RETRIES); do
    if kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --list > /dev/null 2>&1; then
        echo "[create-topics] Kafka is ready (attempt $i/$MAX_RETRIES)"
        break
    fi

    if [ "$i" -eq "$MAX_RETRIES" ]; then
        echo "[create-topics] ERROR: Kafka is not ready after $MAX_RETRIES attempts. Exiting."
        exit 1
    fi

    echo "[create-topics] Kafka is not ready yet (attempt $i/$MAX_RETRIES). Retrying in ${RETRY_INTERVAL}s..."
    sleep "$RETRY_INTERVAL"
done

create_topic() {
    local topic_name=$1
    local partitions=$2
    local replication_factor=$3

    echo "[create-topics] Creating topic: ${topic_name} (partitions=${partitions}, RF=${replication_factor})"

    if kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --describe --topic "$topic_name" > /dev/null 2>&1; then
        echo "[create-topics] Topic '${topic_name}' already exists, skipping."
    else
        kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" \
            --create \
            --topic "$topic_name" \
            --partitions "$partitions" \
            --replication-factor "$replication_factor" \
            --config retention.ms=604800000 \
            --config cleanup.policy=delete

        echo "[create-topics] Topic '${topic_name}' created successfully."
    fi
}

echo "[create-topics] Starting topic creation..."

# Core message topics
create_topic "messagepulse.message.send"      20 3
create_topic "messagepulse.message.status"     10 3
create_topic "messagepulse.message.receipt"    10 3
create_topic "messagepulse.message.retry"       5 3
create_topic "messagepulse.message.dlq"         3 3

# Skill management topics
create_topic "messagepulse.skill.heartbeat"     3 3

# Billing topics
create_topic "messagepulse.billing.event"       5 3

echo "[create-topics] All topics created successfully."
echo "[create-topics] Listing all topics:"
kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --list
