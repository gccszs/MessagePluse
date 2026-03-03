package com.messagepulse.core.engine.statemachine;

import com.messagepulse.core.enums.ErrorCode;
import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.exception.MessagePulseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageStateMachine {

    private final StateTransitionValidator validator;

    public MessageStatus transition(MessageStatus currentStatus, MessageStatus newStatus) {
        if (!validator.validate(currentStatus, newStatus)) {
            throw new MessagePulseException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    String.format("Invalid state transition: %s -> %s", currentStatus, newStatus)
            );
        }

        log.info("State transition: {} -> {}", currentStatus, newStatus);
        return newStatus;
    }
}
