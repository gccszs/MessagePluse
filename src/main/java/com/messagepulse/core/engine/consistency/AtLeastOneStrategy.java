package com.messagepulse.core.engine.consistency;

import com.messagepulse.core.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AtLeastOneStrategy implements ConsistencyEngine {

    @Override
    public boolean evaluate(MessageEvent event, Map<String, Boolean> channelResults) {
        long successCount = channelResults.values().stream().filter(Boolean::booleanValue).count();
        boolean result = successCount >= 1;
        log.debug("AtLeastOne evaluate for message {}: successCount={}, result={}", event.getMessageId(), successCount, result);
        return result;
    }

    @Override
    public List<String> getRequiredChannels(MessageEvent event, List<String> routedChannels) {
        return routedChannels;
    }
}
