package com.messagepulse.core.engine.revoke;

import com.messagepulse.core.entity.Message;
import com.messagepulse.core.enums.ErrorCode;
import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.exception.MessageNotFoundException;
import com.messagepulse.core.exception.MessagePulseException;
import com.messagepulse.core.repository.MessageRepository;
import com.messagepulse.core.service.MessageStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultRevokeEngineTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageStateService messageStateService;

    private DefaultRevokeEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultRevokeEngine(messageRepository, messageStateService);
    }

    @Test
    void testRevoke_MessageNotFound_ThrowsException() {
        String messageId = "msg-001";
        String tenantId = "tenant-1";

        when(messageRepository.findByMessageId(messageId)).thenReturn(Optional.empty());

        assertThrows(MessageNotFoundException.class,
                () -> engine.revoke(messageId, tenantId));

        verify(messageRepository).findByMessageId(messageId);
        verifyNoInteractions(messageStateService);
    }

    @Test
    void testRevoke_WrongTenant_ThrowsException() {
        String messageId = "msg-002";
        String tenantId = "tenant-1";

        Message message = Message.builder()
                .messageId(messageId)
                .tenantId("tenant-2")
                .status(MessageStatus.PENDING)
                .build();

        when(messageRepository.findByMessageId(messageId)).thenReturn(Optional.of(message));

        MessagePulseException exception = assertThrows(
                MessagePulseException.class,
                () -> engine.revoke(messageId, tenantId)
        );

        assertEquals(ErrorCode.INSUFFICIENT_PERMISSIONS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("tenant-1"));
        verifyNoInteractions(messageStateService);
    }

    @Test
    void testRevoke_PendingMessage_Success() {
        String messageId = "msg-003";
        String tenantId = "tenant-1";

        Message message = Message.builder()
                .messageId(messageId)
                .tenantId(tenantId)
                .status(MessageStatus.PENDING)
                .channelType("SMS")
                .build();

        when(messageRepository.findByMessageId(messageId)).thenReturn(Optional.of(message));

        engine.revoke(messageId, tenantId);

        verify(messageStateService).updateState(
                messageId,
                MessageStatus.REVOKED,
                "SMS",
                null,
                "Revoked by user"
        );
    }

    @Test
    void testRevoke_RoutingMessage_Success() {
        String messageId = "msg-004";
        String tenantId = "tenant-1";

        Message message = Message.builder()
                .messageId(messageId)
                .tenantId(tenantId)
                .status(MessageStatus.ROUTING)
                .channelType("EMAIL")
                .build();

        when(messageRepository.findByMessageId(messageId)).thenReturn(Optional.of(message));

        engine.revoke(messageId, tenantId);

        verify(messageStateService).updateState(
                messageId,
                MessageStatus.REVOKED,
                "EMAIL",
                null,
                "Revoked during routing"
        );
    }

    @Test
    void testRevoke_SendingMessage_Success() {
        String messageId = "msg-005";
        String tenantId = "tenant-1";

        Message message = Message.builder()
                .messageId(messageId)
                .tenantId(tenantId)
                .status(MessageStatus.SENDING)
                .channelType("PUSH")
                .build();

        when(messageRepository.findByMessageId(messageId)).thenReturn(Optional.of(message));

        engine.revoke(messageId, tenantId);

        verify(messageStateService).updateState(
                messageId,
                MessageStatus.REVOKED,
                "PUSH",
                null,
                "Revoked during sending"
        );
    }

    @Test
    void testRevoke_SentMessage_Success() {
        String messageId = "msg-006";
        String tenantId = "tenant-1";

        Message message = Message.builder()
                .messageId(messageId)
                .tenantId(tenantId)
                .status(MessageStatus.SENT)
                .channelType("SMS")
                .build();

        when(messageRepository.findByMessageId(messageId)).thenReturn(Optional.of(message));

        engine.revoke(messageId, tenantId);

        verify(messageStateService).updateState(
                messageId,
                MessageStatus.REVOKED,
                "SMS",
                null,
                "Revoked after sent"
        );
    }

    @Test
    void testRevoke_FailedMessage_Success() {
        String messageId = "msg-007";
        String tenantId = "tenant-1";

        Message message = Message.builder()
                .messageId(messageId)
                .tenantId(tenantId)
                .status(MessageStatus.FAILED)
                .channelType("EMAIL")
                .build();

        when(messageRepository.findByMessageId(messageId)).thenReturn(Optional.of(message));

        engine.revoke(messageId, tenantId);

        verify(messageStateService).updateState(
                messageId,
                MessageStatus.REVOKED,
                "EMAIL",
                null,
                "Revoked after failure"
        );
    }

    @Test
    void testRevoke_AlreadyRevokedMessage_NoStateUpdate() {
        String messageId = "msg-008";
        String tenantId = "tenant-1";

        Message message = Message.builder()
                .messageId(messageId)
                .tenantId(tenantId)
                .status(MessageStatus.REVOKED)
                .channelType("SMS")
                .build();

        when(messageRepository.findByMessageId(messageId)).thenReturn(Optional.of(message));

        engine.revoke(messageId, tenantId);

        verifyNoInteractions(messageStateService);
    }
}
