package com.messagepulse.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokeMessageRequest {

    @NotBlank(message = "Message ID is required")
    private String messageId;

    private String reason;
}
