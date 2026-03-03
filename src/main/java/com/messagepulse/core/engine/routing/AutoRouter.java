package com.messagepulse.core.engine.routing;

import com.messagepulse.core.dto.RoutingConfig;
import com.messagepulse.core.enums.RoutingStrategy;
import com.messagepulse.core.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class AutoRouter implements RoutingEngine {

    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    @Override
    public List<String> route(MessageEvent event) {
        RoutingConfig config = event.getRoutingConfig();
        if (config == null || config.getExplicitChannels() == null || config.getExplicitChannels().isEmpty()) {
            log.warn("No channels available for auto routing, message {}", event.getMessageId());
            return Collections.emptyList();
        }

        List<String> availableChannels = config.getExplicitChannels();
        RoutingStrategy strategy = config.getStrategy();
        if (strategy == null) {
            strategy = RoutingStrategy.FAILOVER;
        }

        List<String> result = switch (strategy) {
            case FAILOVER -> handleFailover(availableChannels);
            case LOAD_BALANCE -> handleLoadBalance(availableChannels);
            case BROADCAST -> handleBroadcast(availableChannels);
        };

        log.debug("Auto routing (strategy={}) for message {}: {}", strategy, event.getMessageId(), result);
        return result;
    }

    private List<String> handleFailover(List<String> channels) {
        return List.of(channels.get(0));
    }

    private List<String> handleLoadBalance(List<String> channels) {
        int index = Math.abs(roundRobinCounter.getAndIncrement() % channels.size());
        return List.of(channels.get(index));
    }

    private List<String> handleBroadcast(List<String> channels) {
        return List.copyOf(channels);
    }
}
