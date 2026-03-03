-- MessagePulse Database Initialization Script
-- Character set: utf8mb4, Collation: utf8mb4_unicode_ci

USE messagepulse;

-- -------------------------------------------
-- Table: messages
-- Core message records
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(128) NOT NULL UNIQUE,
    channel_type VARCHAR(32) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    content JSON NOT NULL,
    routing_config JSON,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_messages_tenant_id (tenant_id),
    INDEX idx_messages_status (status),
    INDEX idx_messages_channel_type (channel_type),
    INDEX idx_messages_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- Table: message_states
-- Message delivery state tracking
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS message_states (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    delivery_status VARCHAR(32),
    receipt JSON,
    error_message TEXT,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_message_states_message_id (message_id),
    INDEX idx_message_states_status (status),
    INDEX idx_message_states_channel_type (channel_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- Table: api_keys
-- API key management for tenant authentication
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS api_keys (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    scopes JSON,
    rate_limit INT NOT NULL DEFAULT 100,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    expires_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_api_keys_tenant_id (tenant_id),
    INDEX idx_api_keys_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- Table: skill_configs
-- Channel skill configuration
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS skill_configs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    skill_name VARCHAR(128) NOT NULL,
    config JSON,
    priority INT NOT NULL DEFAULT 0,
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_skill_configs_tenant_id (tenant_id),
    INDEX idx_skill_configs_channel_type (channel_type),
    INDEX idx_skill_configs_is_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- Table: skill_instances
-- Running skill instance registry
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS skill_instances (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    skill_config_id VARCHAR(36) NOT NULL,
    instance_id VARCHAR(128) NOT NULL,
    endpoint VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_heartbeat DATETIME,
    metadata JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skill_instances_skill_config_id (skill_config_id),
    INDEX idx_skill_instances_status (status),
    INDEX idx_skill_instances_instance_id (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- Table: billing_records
-- Message billing and cost tracking
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS billing_records (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    cost DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    billing_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSON,
    INDEX idx_billing_records_tenant_id (tenant_id),
    INDEX idx_billing_records_message_id (message_id),
    INDEX idx_billing_records_billing_time (billing_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- Table: message_templates
-- Reusable message templates
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS message_templates (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    variables JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_message_templates_tenant_id (tenant_id),
    INDEX idx_message_templates_channel_type (channel_type),
    UNIQUE KEY uk_message_templates_tenant_name (tenant_id, template_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- Insert default test API Key
-- key_hash is SHA-256 of "mp-test-key-001"
-- -------------------------------------------
INSERT INTO api_keys (id, tenant_id, key_hash, scopes, rate_limit, is_active, expires_at, created_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'tenant-default',
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    '["message:send", "message:query", "template:manage"]',
    1000,
    1,
    '2099-12-31 23:59:59',
    NOW()
);
