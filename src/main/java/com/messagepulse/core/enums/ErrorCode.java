package com.messagepulse.core.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // 通用错误 (1xxx)
    INTERNAL_ERROR(1000, "Internal server error"),
    INVALID_REQUEST(1001, "Invalid request"),
    RESOURCE_NOT_FOUND(1002, "Resource not found"),

    // 认证授权错误 (2xxx)
    AUTHENTICATION_FAILED(2000, "Authentication failed"),
    INVALID_API_KEY(2001, "Invalid API key"),
    API_KEY_EXPIRED(2002, "API key expired"),
    INSUFFICIENT_PERMISSIONS(2003, "Insufficient permissions"),

    // 消息相关错误 (3xxx)
    MESSAGE_NOT_FOUND(3000, "Message not found"),
    DUPLICATE_MESSAGE(3001, "Duplicate message"),
    INVALID_MESSAGE_CONTENT(3002, "Invalid message content"),
    MESSAGE_ALREADY_SENT(3003, "Message already sent"),
    MESSAGE_REVOKE_FAILED(3004, "Message revoke failed"),

    // 渠道相关错误 (4xxx)
    CHANNEL_NOT_AVAILABLE(4000, "Channel not available"),
    CHANNEL_CONFIG_ERROR(4001, "Channel configuration error"),
    CHANNEL_SEND_FAILED(4002, "Channel send failed"),
    NO_AVAILABLE_CHANNEL(4003, "No available channel"),

    // 限流相关错误 (5xxx)
    RATE_LIMIT_EXCEEDED(5000, "Rate limit exceeded"),
    QUOTA_EXCEEDED(5001, "Quota exceeded"),

    // Skill相关错误 (6xxx)
    SKILL_NOT_FOUND(6000, "Skill not found"),
    SKILL_UNAVAILABLE(6001, "Skill unavailable"),
    SKILL_EXECUTION_FAILED(6002, "Skill execution failed"),

    // 路由相关错误 (7xxx)
    ROUTING_FAILED(7000, "Routing failed"),
    INVALID_ROUTING_CONFIG(7001, "Invalid routing configuration"),

    // 状态机相关错误 (8xxx)
    INVALID_STATE_TRANSITION(8000, "Invalid state transition");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
