package com.messagepulse.core.engine.routing;

import com.messagepulse.core.dto.RecipientInfo;
import com.messagepulse.core.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ImplicitRouter implements RoutingEngine {

    @Override
    public List<String> route(MessageEvent event) {
        List<String> channels = new ArrayList<>();

        if (event.getRecipients() != null) {
            for (RecipientInfo recipient : event.getRecipients()) {
                if (recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
                    if (!channels.contains("EMAIL")) {
                        channels.add("EMAIL");
                    }
                }
                if (recipient.getPhone() != null && !recipient.getPhone().isBlank()) {
                    if (!channels.contains("SMS")) {
                        channels.add("SMS");
                    }
                }
                if (recipient.getUserId() != null && !recipient.getUserId().isBlank()) {
                    if (!channels.contains("IN_APP")) {
                        channels.add("IN_APP");
                    }
                }
            }
        }

        log.debug("Implicit routing for message {}: {}", event.getMessageId(), channels);
        return channels;
    }
}
