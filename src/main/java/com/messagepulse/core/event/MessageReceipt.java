package com.messagepulse.core.event;

import com.messagepulse.core.enums.DeliveryStatus;
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
public class MessageReceipt {

    private String messageId;
    private String channelType;
    private DeliveryStatus deliveryStatus;
    private String externalMessageId;
    private Map<String, Object> metadata;
    private String errorMessage;
    private LocalDateTime timestamp;
}
