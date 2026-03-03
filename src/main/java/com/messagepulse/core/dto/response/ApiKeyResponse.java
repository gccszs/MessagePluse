package com.messagepulse.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyResponse {

    private String id;
    private String tenantId;
    private String apiKey;
    private List<String> scopes;
    private Integer rateLimit;
    private Boolean isActive;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
