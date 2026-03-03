package com.messagepulse.core.engine.routing;

import com.messagepulse.core.dto.RoutingConfig;
import com.messagepulse.core.enums.RoutingMode;
import com.messagepulse.core.event.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultRoutingEngine implements RoutingEngine {

    private final ExplicitRouter explicitRouter;
    private final ImplicitRouter implicitRouter;
    private final AutoRouter autoRouter;

    @Override
    public List<String> route(MessageEvent event) {
        RoutingConfig config = event.getRoutingConfig();
        RoutingMode mode = (config != null && config.getMode() != null) ? config.getMode() : RoutingMode.IMPLICIT;

        log.info("Routing message {} with mode {}", event.getMessageId(), mode);

        return switch (mode) {
            case EXPLICIT -> explicitRouter.route(event);
            case IMPLICIT -> implicitRouter.route(event);
            case AUTO -> autoRouter.route(event);
        };
    }
}
