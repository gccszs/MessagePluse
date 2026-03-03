package com.messagepulse.core.engine.routing;

import com.messagepulse.core.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ExplicitRouter implements RoutingEngine {

    @Override
    public List<String> route(MessageEvent event) {
        if (event.getRoutingConfig() == null || event.getRoutingConfig().getExplicitChannels() == null) {
            log.warn("No explicit channels configured for message {}", event.getMessageId());
            return Collections.emptyList();
        }

        List<String> channels = event.getRoutingConfig().getExplicitChannels();
        log.debug("Explicit routing for message {}: {}", event.getMessageId(), channels);
        return channels;
    }
}
