package com.messagepulse.core.dto.response;

import com.messagepulse.core.enums.DeliveryStatus;
import com.messagepulse.core.enums.MessageStatus;
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
public class MessageStatusResponse {

    private String messageId;
    private MessageStatus status;
    private DeliveryStatus deliveryStatus;
    private Map<String, Object> receipt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
