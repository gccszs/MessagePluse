package com.messagepulse.core.exception;

import com.messagepulse.core.enums.ErrorCode;

public class AuthorizationException extends MessagePulseException {

    public AuthorizationException(String message) {
        super(ErrorCode.INSUFFICIENT_PERMISSIONS, message);
    }
}
