package com.messagepulse.core.engine.routing;

import com.messagepulse.core.dto.RoutingConfig;
import com.messagepulse.core.enums.RoutingMode;
import com.messagepulse.core.event.MessageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultRoutingEngineTest {

    @Mock
    private ExplicitRouter explicitRouter;

    @Mock
    private ImplicitRouter implicitRouter;

    @Mock
    private AutoRouter autoRouter;

    private DefaultRoutingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultRoutingEngine(explicitRouter, implicitRouter, autoRouter);
    }

    @Test
    void testRoute_ExplicitMode_UsesExplicitRouter() {
        MessageEvent event = MessageEvent.builder()
                .messageId("msg-001")
                .routingConfig(RoutingConfig.builder()
                        .mode(RoutingMode.EXPLICIT)
                        .build())
                .build();

        List<String> expectedChannels = Arrays.asList("channel-1", "channel-2");
        when(explicitRouter.route(event)).thenReturn(expectedChannels);

        List<String> result = engine.route(event);

        assertEquals(expectedChannels, result);
        verify(explicitRouter).route(event);
        verifyNoInteractions(implicitRouter, autoRouter);
    }

    @Test
    void testRoute_ImplicitMode_UsesImplicitRouter() {
        MessageEvent event = MessageEvent.builder()
                .messageId("msg-002")
                .routingConfig(RoutingConfig.builder()
                        .mode(RoutingMode.IMPLICIT)
                        .build())
                .build();

        List<String> expectedChannels = Arrays.asList("channel-3");
        when(implicitRouter.route(event)).thenReturn(expectedChannels);

        List<String> result = engine.route(event);

        assertEquals(expectedChannels, result);
        verify(implicitRouter).route(event);
        verifyNoInteractions(explicitRouter, autoRouter);
    }

    @Test
    void testRoute_AutoMode_UsesAutoRouter() {
        MessageEvent event = MessageEvent.builder()
                .messageId("msg-003")
                .routingConfig(RoutingConfig.builder()
                        .mode(RoutingMode.AUTO)
                        .build())
                .build();

        List<String> expectedChannels = Arrays.asList("channel-4", "channel-5");
        when(autoRouter.route(event)).thenReturn(expectedChannels);

        List<String> result = engine.route(event);

        assertEquals(expectedChannels, result);
        verify(autoRouter).route(event);
        verifyNoInteractions(explicitRouter, implicitRouter);
    }

    @Test
    void testRoute_NullRoutingConfig_DefaultsToImplicit() {
        MessageEvent event = MessageEvent.builder()
                .messageId("msg-004")
                .routingConfig(null)
                .build();

        List<String> expectedChannels = Arrays.asList("channel-6");
        when(implicitRouter.route(event)).thenReturn(expectedChannels);

        List<String> result = engine.route(event);

        assertEquals(expectedChannels, result);
        verify(implicitRouter).route(event);
        verifyNoInteractions(explicitRouter, autoRouter);
    }

    @Test
    void testRoute_NullMode_DefaultsToImplicit() {
        MessageEvent event = MessageEvent.builder()
                .messageId("msg-005")
                .routingConfig(RoutingConfig.builder()
                        .mode(null)
                        .build())
                .build();

        List<String> expectedChannels = Arrays.asList("channel-7");
        when(implicitRouter.route(event)).thenReturn(expectedChannels);

        List<String> result = engine.route(event);

        assertEquals(expectedChannels, result);
        verify(implicitRouter).route(event);
        verifyNoInteractions(explicitRouter, autoRouter);
    }
}
