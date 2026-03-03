package com.messagepulse.core.engine.statemachine;

import com.messagepulse.core.enums.MessageStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class StateTransitionValidator {

    private final Map<MessageStatus, Set<MessageStatus>> validTransitions;

    public StateTransitionValidator() {
        validTransitions = new EnumMap<>(MessageStatus.class);

        validTransitions.put(MessageStatus.PENDING, EnumSet.of(MessageStatus.ROUTING, MessageStatus.REVOKED));
        validTransitions.put(MessageStatus.ROUTING, EnumSet.of(MessageStatus.SENDING, MessageStatus.FAILED, MessageStatus.REVOKED));
        validTransitions.put(MessageStatus.SENDING, EnumSet.of(MessageStatus.SENT, MessageStatus.FAILED, MessageStatus.REVOKED));
        validTransitions.put(MessageStatus.SENT, EnumSet.of(MessageStatus.REVOKED));
        validTransitions.put(MessageStatus.FAILED, EnumSet.of(MessageStatus.SENDING, MessageStatus.REVOKED));
        validTransitions.put(MessageStatus.REVOKED, EnumSet.noneOf(MessageStatus.class));
    }

    public boolean validate(MessageStatus from, MessageStatus to) {
        if (from == null || to == null) {
            log.warn("Cannot validate null status transition");
            return false;
        }

        Set<MessageStatus> allowedTransitions = validTransitions.get(from);
        boolean isValid = allowedTransitions != null && allowedTransitions.contains(to);

        if (!isValid) {
            log.warn("Invalid state transition: {} -> {}", from, to);
        }

        return isValid;
    }
}
