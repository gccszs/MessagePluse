package com.messagepulse.core.exception;

import com.messagepulse.core.enums.ErrorCode;

public class AuthenticationException extends MessagePulseException {

    public AuthenticationException(String message) {
        super(ErrorCode.AUTHENTICATION_FAILED, message);
    }
}
