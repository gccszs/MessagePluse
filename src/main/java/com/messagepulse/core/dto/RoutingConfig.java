package com.messagepulse.core.dto;

import com.messagepulse.core.enums.ConsistencyStrategy;
import com.messagepulse.core.enums.RoutingMode;
import com.messagepulse.core.enums.RoutingStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingConfig {

    private RoutingMode mode;
    private RoutingStrategy strategy;
    private ConsistencyStrategy consistencyStrategy;
    private List<String> explicitChannels;
    private Integer maxRetries;
    private Long retryDelayMs;
}
