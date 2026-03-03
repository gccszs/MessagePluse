package com.messagepulse.core.service;

import com.messagepulse.core.engine.statemachine.MessageStateMachine;
import com.messagepulse.core.entity.Message;
import com.messagepulse.core.entity.MessageState;
import com.messagepulse.core.enums.DeliveryStatus;
import com.messagepulse.core.enums.ErrorCode;
import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.exception.MessageNotFoundException;
import com.messagepulse.core.repository.MessageRepository;
import com.messagepulse.core.repository.MessageStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStateService {

    private final MessageRepository messageRepository;
    private final MessageStateRepository messageStateRepository;
    private final MessageStateMachine stateMachine;

    @Transactional
    public void updateState(String messageId, MessageStatus newStatus, String channelType, String receipt, String errorMessage) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        MessageStatus validatedStatus = stateMachine.transition(message.getStatus(), newStatus);

        MessageState state = MessageState.builder()
                .messageId(messageId)
                .channelType(channelType)
                .status(validatedStatus)
                .deliveryStatus(determineDeliveryStatus(validatedStatus))
                .receipt(receipt)
                .errorMessage(errorMessage)
                .attemptCount(0)
                .build();

        messageStateRepository.save(state);

        message.setStatus(validatedStatus);
        messageRepository.save(message);

        log.info("Updated message {} state to {}", messageId, validatedStatus);
    }

    private DeliveryStatus determineDeliveryStatus(MessageStatus status) {
        return switch (status) {
            case SENT -> DeliveryStatus.SUCCESS;
            case FAILED -> DeliveryStatus.FAILED;
            default -> null;
        };
    }
}
