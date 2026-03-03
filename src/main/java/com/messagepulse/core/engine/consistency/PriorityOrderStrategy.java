package com.messagepulse.core.engine.consistency;

import com.messagepulse.core.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PriorityOrderStrategy implements ConsistencyEngine {

    @Override
    public boolean evaluate(MessageEvent event, Map<String, Boolean> channelResults) {
        if (event.getRoutingConfig() == null || event.getRoutingConfig().getExplicitChannels() == null) {
            return false;
        }

        List<String> priorityOrder = event.getRoutingConfig().getExplicitChannels();

        for (String channel : priorityOrder) {
            Boolean result = channelResults.get(channel);
            if (result != null && result) {
                log.debug("PriorityOrder evaluate for message {}: first success channel={}", event.getMessageId(), channel);
                return true;
            }
        }

        log.debug("PriorityOrder evaluate for message {}: no channel succeeded", event.getMessageId());
        return false;
    }

    @Override
    public List<String> getRequiredChannels(MessageEvent event, List<String> routedChannels) {
        if (event.getRoutingConfig() == null || event.getRoutingConfig().getExplicitChannels() == null) {
            return routedChannels;
        }

        List<String> priorityOrder = event.getRoutingConfig().getExplicitChannels();
        List<String> required = new ArrayList<>();

        for (String channel : priorityOrder) {
            if (routedChannels.contains(channel)) {
                required.add(channel);
                break;
            }
        }

        return required.isEmpty() ? routedChannels : required;
    }
}
