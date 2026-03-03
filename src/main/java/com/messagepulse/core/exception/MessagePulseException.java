package com.messagepulse.core.exception;

import com.messagepulse.core.enums.ErrorCode;
import lombok.Getter;

@Getter
public class MessagePulseException extends RuntimeException {

    private final ErrorCode errorCode;

    public MessagePulseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public MessagePulseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MessagePulseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
