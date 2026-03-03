package com.messagepulse.core.service;

import com.messagepulse.core.entity.Message;
import com.messagepulse.core.entity.MessageState;
import com.messagepulse.core.enums.DeliveryStatus;
import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.event.MessageReceipt;
import com.messagepulse.core.repository.MessageRepository;
import com.messagepulse.core.repository.MessageStateRepository;
import com.messagepulse.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptProcessService {

    private final MessageRepository messageRepository;
    private final MessageStateRepository messageStateRepository;

    @Transactional
    public void processReceipt(MessageReceipt receipt) {
        String messageId = receipt.getMessageId();
        String channelType = receipt.getChannelType();

        log.info("Processing receipt for messageId: {}, channelType: {}, deliveryStatus: {}",
                messageId, channelType, receipt.getDeliveryStatus());

        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        MessageStatus messageStatus = mapDeliveryStatusToMessageStatus(receipt.getDeliveryStatus());

        MessageState state = MessageState.builder()
                .messageId(messageId)
                .channelType(channelType)
                .status(messageStatus)
                .deliveryStatus(receipt.getDeliveryStatus())
                .receipt(receipt.getMetadata() != null ? JsonUtils.toJson(receipt.getMetadata()) : null)
                .errorMessage(receipt.getErrorMessage())
                .attemptCount(0)
                .build();

        messageStateRepository.save(state);

        updateMessageStatus(message, messageStatus);

        log.info("Receipt processed successfully for messageId: {}", messageId);
    }

    private void updateMessageStatus(Message message, MessageStatus newStatus) {
        if (shouldUpdateMessageStatus(message.getStatus(), newStatus)) {
            message.setStatus(newStatus);
            messageRepository.save(message);
            log.debug("Updated message status to {} for messageId: {}", newStatus, message.getMessageId());
        }
    }

    private boolean shouldUpdateMessageStatus(MessageStatus currentStatus, MessageStatus newStatus) {
        if (currentStatus == MessageStatus.SENT || currentStatus == MessageStatus.FAILED) {
            return false;
        }
        return newStatus == MessageStatus.SENT || newStatus == MessageStatus.FAILED;
    }

    private MessageStatus mapDeliveryStatusToMessageStatus(DeliveryStatus deliveryStatus) {
        return switch (deliveryStatus) {
            case SUCCESS -> MessageStatus.SENT;
            case FAILED -> MessageStatus.FAILED;
            case PARTIAL -> MessageStatus.SENDING;
        };
    }
}
