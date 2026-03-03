package com.messagepulse.core.exception;

import com.messagepulse.core.enums.ErrorCode;

public class DuplicateMessageException extends MessagePulseException {

    public DuplicateMessageException(String messageId) {
        super(ErrorCode.DUPLICATE_MESSAGE, "Duplicate message: " + messageId);
    }
}
