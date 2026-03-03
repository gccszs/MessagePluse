package com.messagepulse.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageResponse {

    private String messageId;
    private String status;
    private LocalDateTime createdAt;
}
