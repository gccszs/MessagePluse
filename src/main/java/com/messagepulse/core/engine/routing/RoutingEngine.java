package com.messagepulse.core.engine.routing;

import com.messagepulse.core.event.MessageEvent;

import java.util.List;

public interface RoutingEngine {

    List<String> route(MessageEvent event);
}
