package com.messagepulse.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateApiKeyRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotNull(message = "Scopes are required")
    private List<String> scopes;

    @Builder.Default
    private Integer rateLimit = 100;

    private LocalDateTime expiresAt;
}
