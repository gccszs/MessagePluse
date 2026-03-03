package com.messagepulse.core.engine.consistency;

import com.messagepulse.core.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EventualConsistencyStrategy implements ConsistencyEngine {

    @Override
    public boolean evaluate(MessageEvent event, Map<String, Boolean> channelResults) {
        boolean anySuccess = channelResults.values().stream().anyMatch(Boolean::booleanValue);
        log.debug("EventualConsistency evaluate for message {}: anySuccess={}", event.getMessageId(), anySuccess);
        return anySuccess;
    }

    @Override
    public List<String> getRequiredChannels(MessageEvent event, List<String> routedChannels) {
        return routedChannels;
    }
}
