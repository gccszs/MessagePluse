package com.messagepulse.core.controller;

import com.messagepulse.core.dto.response.MessageStatusResponse;
import com.messagepulse.core.entity.Message;
import com.messagepulse.core.entity.MessageState;
import com.messagepulse.core.exception.MessageNotFoundException;
import com.messagepulse.core.repository.MessageRepository;
import com.messagepulse.core.repository.MessageStateRepository;
import com.messagepulse.core.security.RequireScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageStatusController {

    private final MessageRepository messageRepository;
    private final MessageStateRepository messageStateRepository;

    public MessageStatusController(MessageRepository messageRepository,
                                   MessageStateRepository messageStateRepository) {
        this.messageRepository = messageRepository;
        this.messageStateRepository = messageStateRepository;
    }

    @GetMapping("/{messageId}/status")
    @RequireScope("message:read")
    public ResponseEntity<MessageStatusResponse> getMessageStatus(@PathVariable String messageId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        MessageStatusResponse response = MessageStatusResponse.builder()
                .messageId(message.getMessageId())
                .status(message.getStatus())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{messageId}/receipts")
    @RequireScope("message:read")
    public ResponseEntity<List<MessageState>> getMessageReceipts(@PathVariable String messageId) {
        if (!messageRepository.existsByMessageId(messageId)) {
            throw new MessageNotFoundException(messageId);
        }

        List<MessageState> states = messageStateRepository.findByMessageIdOrderByCreatedAtDesc(messageId);
        return ResponseEntity.ok(states);
    }
}
