#!/bin/bash
# MessagePulse - Database Initialization Script
# Waits for MySQL to be ready and executes init.sql

set -e

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-messagepulse_root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-messagepulse}"
MAX_RETRIES=30
RETRY_INTERVAL=5

echo "[init-db] Waiting for MySQL at ${MYSQL_HOST}:${MYSQL_PORT}..."

for i in $(seq 1 $MAX_RETRIES); do
    if mysqladmin ping -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" --silent 2>/dev/null; then
        echo "[init-db] MySQL is ready (attempt $i/$MAX_RETRIES)"
        break
    fi

    if [ "$i" -eq "$MAX_RETRIES" ]; then
        echo "[init-db] ERROR: MySQL is not ready after $MAX_RETRIES attempts. Exiting."
        exit 1
    fi

    echo "[init-db] MySQL is not ready yet (attempt $i/$MAX_RETRIES). Retrying in ${RETRY_INTERVAL}s..."
    sleep "$RETRY_INTERVAL"
done

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/../sql/init.sql"

if [ ! -f "$SQL_FILE" ]; then
    echo "[init-db] ERROR: SQL file not found: $SQL_FILE"
    exit 1
fi

echo "[init-db] Executing init.sql on database '${MYSQL_DATABASE}'..."
mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < "$SQL_FILE"

echo "[init-db] Database initialization completed successfully."
