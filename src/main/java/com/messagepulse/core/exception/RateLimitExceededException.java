package com.messagepulse.core.exception;

import com.messagepulse.core.enums.ErrorCode;

public class RateLimitExceededException extends MessagePulseException {

    public RateLimitExceededException(String tenantId) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, "Rate limit exceeded for tenant: " + tenantId);
    }
}
