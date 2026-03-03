package com.messagepulse.core.engine.consistency;

import com.messagepulse.core.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AllOrNoneStrategy implements ConsistencyEngine {

    @Override
    public boolean evaluate(MessageEvent event, Map<String, Boolean> channelResults) {
        boolean allSuccess = channelResults.values().stream().allMatch(Boolean::booleanValue);
        log.debug("AllOrNone evaluate for message {}: allSuccess={}", event.getMessageId(), allSuccess);
        return allSuccess;
    }

    @Override
    public List<String> getRequiredChannels(MessageEvent event, List<String> routedChannels) {
        return routedChannels;
    }
}
