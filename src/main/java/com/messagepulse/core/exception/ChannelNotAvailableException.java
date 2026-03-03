package com.messagepulse.core.exception;

import com.messagepulse.core.enums.ErrorCode;

public class ChannelNotAvailableException extends MessagePulseException {

    public ChannelNotAvailableException(String channelType) {
        super(ErrorCode.CHANNEL_NOT_AVAILABLE, "Channel not available: " + channelType);
    }
}
