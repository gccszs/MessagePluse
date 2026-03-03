package com.messagepulse.core.exception;

import com.messagepulse.core.enums.ErrorCode;

public class MessageNotFoundException extends MessagePulseException {

    public MessageNotFoundException(String messageId) {
        super(ErrorCode.MESSAGE_NOT_FOUND, "Message not found: " + messageId);
    }
}
