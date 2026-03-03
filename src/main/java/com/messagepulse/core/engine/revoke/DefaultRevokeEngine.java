package com.messagepulse.core.engine.revoke;

import com.messagepulse.core.entity.Message;
import com.messagepulse.core.enums.ErrorCode;
import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.exception.MessageNotFoundException;
import com.messagepulse.core.exception.MessagePulseException;
import com.messagepulse.core.repository.MessageRepository;
import com.messagepulse.core.service.MessageStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRevokeEngine implements RevokeEngine {

    private final MessageRepository messageRepository;
    private final MessageStateService messageStateService;

    @Override
    @Transactional
    public void revoke(String messageId, String tenantId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        if (!message.getTenantId().equals(tenantId)) {
            throw new MessagePulseException(
                    ErrorCode.INSUFFICIENT_PERMISSIONS,
                    "Message does not belong to tenant: " + tenantId
            );
        }

        MessageStatus currentStatus = message.getStatus();

        switch (currentStatus) {
            case PENDING -> revokePending(message);
            case ROUTING -> revokeRouting(message);
            case SENDING -> revokeSending(message);
            case SENT -> revokeSent(message);
            case FAILED -> revokeFailed(message);
            case REVOKED -> {
                log.warn("Message {} is already revoked", messageId);
            }
        }
    }

    private void revokePending(Message message) {
        log.info("Revoking PENDING message: {}", message.getMessageId());
        messageStateService.updateState(
                message.getMessageId(),
                MessageStatus.REVOKED,
                message.getChannelType(),
                null,
                "Revoked by user"
        );
    }

    private void revokeRouting(Message message) {
        log.info("Revoking ROUTING message: {}", message.getMessageId());
        messageStateService.updateState(
                message.getMessageId(),
                MessageStatus.REVOKED,
                message.getChannelType(),
                null,
                "Revoked during routing"
        );
    }

    private void revokeSending(Message message) {
        log.info("Revoking SENDING message: {}", message.getMessageId());
        messageStateService.updateState(
                message.getMessageId(),
                MessageStatus.REVOKED,
                message.getChannelType(),
                null,
                "Revoked during sending"
        );
    }

    private void revokeSent(Message message) {
        log.info("Marking SENT message as revoked: {}", message.getMessageId());
        messageStateService.updateState(
                message.getMessageId(),
                MessageStatus.REVOKED,
                message.getChannelType(),
                null,
                "Revoked after sent"
        );
    }

    private void revokeFailed(Message message) {
        log.info("Revoking FAILED message: {}", message.getMessageId());
        messageStateService.updateState(
                message.getMessageId(),
                MessageStatus.REVOKED,
                message.getChannelType(),
                null,
                "Revoked after failure"
        );
    }
}
