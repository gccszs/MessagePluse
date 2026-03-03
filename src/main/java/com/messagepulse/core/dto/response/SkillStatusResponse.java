package com.messagepulse.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillStatusResponse {

    private String skillConfigId;
    private String skillName;
    private String channelType;
    private Boolean isEnabled;
    private Integer activeInstances;
    private Map<String, Object> config;
    private LocalDateTime lastHeartbeat;
}
