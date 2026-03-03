package com.messagepulse.core.engine.statemachine;

import com.messagepulse.core.enums.ErrorCode;
import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.exception.MessagePulseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageStateMachineTest {

    @Mock
    private StateTransitionValidator validator;

    private MessageStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new MessageStateMachine(validator);
    }

    @Test
    void testTransition_ValidTransition_ReturnsNewStatus() {
        MessageStatus currentStatus = MessageStatus.PENDING;
        MessageStatus newStatus = MessageStatus.ROUTING;

        when(validator.validate(currentStatus, newStatus)).thenReturn(true);

        MessageStatus result = stateMachine.transition(currentStatus, newStatus);

        assertEquals(newStatus, result);
        verify(validator).validate(currentStatus, newStatus);
    }

    @Test
    void testTransition_InvalidTransition_ThrowsException() {
        MessageStatus currentStatus = MessageStatus.SENT;
        MessageStatus newStatus = MessageStatus.PENDING;

        when(validator.validate(currentStatus, newStatus)).thenReturn(false);

        MessagePulseException exception = assertThrows(
                MessagePulseException.class,
                () -> stateMachine.transition(currentStatus, newStatus)
        );

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("SENT -> PENDING"));
        verify(validator).validate(currentStatus, newStatus);
    }

    @Test
    void testTransition_PendingToRouting_Success() {
        when(validator.validate(MessageStatus.PENDING, MessageStatus.ROUTING)).thenReturn(true);

        MessageStatus result = stateMachine.transition(MessageStatus.PENDING, MessageStatus.ROUTING);

        assertEquals(MessageStatus.ROUTING, result);
    }

    @Test
    void testTransition_RoutingToSending_Success() {
        when(validator.validate(MessageStatus.ROUTING, MessageStatus.SENDING)).thenReturn(true);

        MessageStatus result = stateMachine.transition(MessageStatus.ROUTING, MessageStatus.SENDING);

        assertEquals(MessageStatus.SENDING, result);
    }

    @Test
    void testTransition_SendingToSent_Success() {
        when(validator.validate(MessageStatus.SENDING, MessageStatus.SENT)).thenReturn(true);

        MessageStatus result = stateMachine.transition(MessageStatus.SENDING, MessageStatus.SENT);

        assertEquals(MessageStatus.SENT, result);
    }

    @Test
    void testTransition_SendingToFailed_Success() {
        when(validator.validate(MessageStatus.SENDING, MessageStatus.FAILED)).thenReturn(true);

        MessageStatus result = stateMachine.transition(MessageStatus.SENDING, MessageStatus.FAILED);

        assertEquals(MessageStatus.FAILED, result);
    }

    @Test
    void testTransition_FailedToSending_Success() {
        when(validator.validate(MessageStatus.FAILED, MessageStatus.SENDING)).thenReturn(true);

        MessageStatus result = stateMachine.transition(MessageStatus.FAILED, MessageStatus.SENDING);

        assertEquals(MessageStatus.SENDING, result);
    }

    @Test
    void testTransition_AnyToRevoked_Success() {
        when(validator.validate(MessageStatus.PENDING, MessageStatus.REVOKED)).thenReturn(true);
        when(validator.validate(MessageStatus.ROUTING, MessageStatus.REVOKED)).thenReturn(true);
        when(validator.validate(MessageStatus.SENDING, MessageStatus.REVOKED)).thenReturn(true);
        when(validator.validate(MessageStatus.SENT, MessageStatus.REVOKED)).thenReturn(true);

        assertEquals(MessageStatus.REVOKED, stateMachine.transition(MessageStatus.PENDING, MessageStatus.REVOKED));
        assertEquals(MessageStatus.REVOKED, stateMachine.transition(MessageStatus.ROUTING, MessageStatus.REVOKED));
        assertEquals(MessageStatus.REVOKED, stateMachine.transition(MessageStatus.SENDING, MessageStatus.REVOKED));
        assertEquals(MessageStatus.REVOKED, stateMachine.transition(MessageStatus.SENT, MessageStatus.REVOKED));
    }

    @Test
    void testTransition_RevokedToAny_Fails() {
        when(validator.validate(eq(MessageStatus.REVOKED), any())).thenReturn(false);

        assertThrows(MessagePulseException.class,
                () -> stateMachine.transition(MessageStatus.REVOKED, MessageStatus.PENDING));
        assertThrows(MessagePulseException.class,
                () -> stateMachine.transition(MessageStatus.REVOKED, MessageStatus.ROUTING));
        assertThrows(MessagePulseException.class,
                () -> stateMachine.transition(MessageStatus.REVOKED, MessageStatus.SENDING));
    }
}
