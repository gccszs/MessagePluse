package com.messagepulse.core.engine.consistency;

import com.messagepulse.core.event.MessageEvent;

import java.util.List;
import java.util.Map;

public interface ConsistencyEngine {

    boolean evaluate(MessageEvent event, Map<String, Boolean> channelResults);

    List<String> getRequiredChannels(MessageEvent event, List<String> routedChannels);
}
