package com.messagepulse.core.service;

import com.messagepulse.core.constant.KafkaTopics;
import com.messagepulse.core.dto.request.SendMessageRequest;
import com.messagepulse.core.dto.response.SendMessageResponse;
import com.messagepulse.core.entity.Message;
import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.event.MessageEvent;
import com.messagepulse.core.exception.DuplicateMessageException;
import com.messagepulse.core.repository.MessageRepository;
import com.messagepulse.core.security.ApiKeyAuthentication;
import com.messagepulse.core.util.JsonUtils;
import com.messagepulse.core.util.MessageIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MessageSendService {

    private final MessageRepository messageRepository;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    public MessageSendService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Transactional
    public SendMessageResponse sendMessage(SendMessageRequest request) {
        if (messageRepository.existsByMessageId(request.getMessageId())) {
            throw new DuplicateMessageException(request.getMessageId());
        }

        String tenantId = extractTenantId();

        Message message = Message.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .messageId(request.getMessageId())
                .channelType(request.getChannelType())
                .priority(request.getPriority())
                .content(JsonUtils.toJson(request.getContent()))
                .routingConfig(request.getRoutingConfig() != null ? JsonUtils.toJson(request.getRoutingConfig()) : null)
                .status(MessageStatus.PENDING)
                .build();

        messageRepository.save(message);

        publishToKafka(request, message);

        return SendMessageResponse.builder()
                .messageId(message.getMessageId())
                .status(message.getStatus().name())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private void publishToKafka(SendMessageRequest request, Message message) {
        if (kafkaTemplate == null) {
            return;
        }

        MessageEvent event = MessageEvent.builder()
                .messageId(message.getMessageId())
                .tenantId(message.getTenantId())
                .channelType(message.getChannelType())
                .priority(message.getPriority())
                .status(message.getStatus())
                .recipients(request.getRecipients())
                .content(request.getContent())
                .routingConfig(request.getRoutingConfig())
                .attemptCount(0)
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(KafkaTopics.MESSAGE_SEND, message.getMessageId(), JsonUtils.toJson(event));
    }

    private String extractTenantId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
            return apiKeyAuth.getTenantId();
        }
        return "default";
    }
}
