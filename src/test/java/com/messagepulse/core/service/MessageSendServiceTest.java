package com.messagepulse.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.messagepulse.core.constant.KafkaTopics;
import com.messagepulse.core.dto.MessageContent;
import com.messagepulse.core.dto.RecipientInfo;
import com.messagepulse.core.dto.RoutingConfig;
import com.messagepulse.core.dto.request.SendMessageRequest;
import com.messagepulse.core.dto.response.SendMessageResponse;
import com.messagepulse.core.entity.Message;
import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.enums.Priority;
import com.messagepulse.core.exception.DuplicateMessageException;
import com.messagepulse.core.repository.MessageRepository;
import com.messagepulse.core.security.ApiKeyAuthentication;
import com.messagepulse.core.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageSendServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private ApiKeyAuthentication authentication;

    private MessageSendService service;

    @BeforeEach
    void setUp() {
        service = new MessageSendService(messageRepository);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testSendMessage_Success_WithKafka() {
        String messageId = "msg-001";
        String tenantId = "tenant-1";
        String channelType = "SMS";

        SendMessageRequest request = SendMessageRequest.builder()
                .messageId(messageId)
                .channelType(channelType)
                .priority(Priority.HIGH)
                .recipients(Arrays.asList(
                        RecipientInfo.builder().phone("+1234567890").build()
                ))
                .content(MessageContent.builder()
                        .subject("Test")
                        .body("Test message")
                        .build())
                .build();

        when(messageRepository.existsByMessageId(messageId)).thenReturn(false);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getTenantId()).thenReturn(tenantId);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        ReflectionTestUtils.setField(service, "kafkaTemplate", kafkaTemplate);

        try (MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {
            jsonUtilsMock.when(() -> JsonUtils.toJson(any())).thenReturn("{}");

            SendMessageResponse response = service.sendMessage(request);

            assertNotNull(response);
            assertEquals(messageId, response.getMessageId());
            assertEquals(MessageStatus.PENDING.name(), response.getStatus());
            assertNotNull(response.getCreatedAt());

            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(messageCaptor.capture());

            Message savedMessage = messageCaptor.getValue();
            assertEquals(tenantId, savedMessage.getTenantId());
            assertEquals(messageId, savedMessage.getMessageId());
            assertEquals(channelType, savedMessage.getChannelType());
            assertEquals(Priority.HIGH, savedMessage.getPriority());
            assertEquals(MessageStatus.PENDING, savedMessage.getStatus());

            verify(kafkaTemplate).send(eq(KafkaTopics.MESSAGE_SEND), eq(messageId), anyString());
        }
    }

    @Test
    void testSendMessage_Success_WithoutKafka() {
        String messageId = "msg-002";
        String tenantId = "tenant-1";

        SendMessageRequest request = SendMessageRequest.builder()
                .messageId(messageId)
                .channelType("EMAIL")
                .priority(Priority.NORMAL)
                .recipients(Arrays.asList(
                        RecipientInfo.builder().email("test@example.com").build()
                ))
                .content(MessageContent.builder()
                        .subject("Test")
                        .body("Test message")
                        .build())
                .build();

        when(messageRepository.existsByMessageId(messageId)).thenReturn(false);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getTenantId()).thenReturn(tenantId);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        try (MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {
            jsonUtilsMock.when(() -> JsonUtils.toJson(any())).thenReturn("{}");

            SendMessageResponse response = service.sendMessage(request);

            assertNotNull(response);
            assertEquals(messageId, response.getMessageId());
            assertEquals(MessageStatus.PENDING.name(), response.getStatus());

            verify(messageRepository).save(any(Message.class));
            verifyNoInteractions(kafkaTemplate);
        }
    }

    @Test
    void testSendMessage_DuplicateMessage_ThrowsException() {
        String messageId = "msg-003";

        SendMessageRequest request = SendMessageRequest.builder()
                .messageId(messageId)
                .channelType("SMS")
                .recipients(Arrays.asList(
                        RecipientInfo.builder().phone("+1234567890").build()
                ))
                .content(MessageContent.builder()
                        .subject("Test")
                        .body("Test message")
                        .build())
                .build();

        when(messageRepository.existsByMessageId(messageId)).thenReturn(true);

        assertThrows(DuplicateMessageException.class,
                () -> service.sendMessage(request));

        verify(messageRepository).existsByMessageId(messageId);
        verify(messageRepository, never()).save(any(Message.class));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void testSendMessage_WithRoutingConfig_SavesConfig() {
        String messageId = "msg-004";
        String tenantId = "tenant-1";

        RoutingConfig routingConfig = RoutingConfig.builder()
                .mode(com.messagepulse.core.enums.RoutingMode.EXPLICIT)
                .build();

        SendMessageRequest request = SendMessageRequest.builder()
                .messageId(messageId)
                .channelType("PUSH")
                .priority(Priority.LOW)
                .recipients(Arrays.asList(
                        RecipientInfo.builder()
                                .customFields(Map.of("deviceToken", "token-123"))
                                .build()
                ))
                .content(MessageContent.builder()
                        .subject("Test")
                        .body("Test message")
                        .build())
                .routingConfig(routingConfig)
                .build();

        when(messageRepository.existsByMessageId(messageId)).thenReturn(false);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getTenantId()).thenReturn(tenantId);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        try (MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {
            jsonUtilsMock.when(() -> JsonUtils.toJson(any())).thenReturn("{\"mode\":\"EXPLICIT\"}");

            SendMessageResponse response = service.sendMessage(request);

            assertNotNull(response);
            assertEquals(messageId, response.getMessageId());

            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(messageCaptor.capture());

            Message savedMessage = messageCaptor.getValue();
            assertNotNull(savedMessage.getRoutingConfig());
        }
    }

    @Test
    void testSendMessage_NoAuthentication_UsesDefaultTenant() {
        String messageId = "msg-005";

        SendMessageRequest request = SendMessageRequest.builder()
                .messageId(messageId)
                .channelType("SMS")
                .priority(Priority.NORMAL)
                .recipients(Arrays.asList(
                        RecipientInfo.builder().phone("+1234567890").build()
                ))
                .content(MessageContent.builder()
                        .subject("Test")
                        .body("Test message")
                        .build())
                .build();

        when(messageRepository.existsByMessageId(messageId)).thenReturn(false);
        when(securityContext.getAuthentication()).thenReturn(null);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        try (MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {
            jsonUtilsMock.when(() -> JsonUtils.toJson(any())).thenReturn("{}");

            SendMessageResponse response = service.sendMessage(request);

            assertNotNull(response);

            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(messageCaptor.capture());

            Message savedMessage = messageCaptor.getValue();
            assertEquals("default", savedMessage.getTenantId());
        }
    }

    @Test
    void testSendMessage_NullRoutingConfig_SavesNull() {
        String messageId = "msg-006";
        String tenantId = "tenant-1";

        SendMessageRequest request = SendMessageRequest.builder()
                .messageId(messageId)
                .channelType("EMAIL")
                .priority(Priority.NORMAL)
                .recipients(Arrays.asList(
                        RecipientInfo.builder().email("test@example.com").build()
                ))
                .content(MessageContent.builder()
                        .subject("Test")
                        .body("Test message")
                        .build())
                .routingConfig(null)
                .build();

        when(messageRepository.existsByMessageId(messageId)).thenReturn(false);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getTenantId()).thenReturn(tenantId);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        try (MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {
            jsonUtilsMock.when(() -> JsonUtils.toJson(any())).thenReturn("{}");

            SendMessageResponse response = service.sendMessage(request);

            assertNotNull(response);

            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(messageCaptor.capture());

            Message savedMessage = messageCaptor.getValue();
            assertNull(savedMessage.getRoutingConfig());
        }
    }
}
