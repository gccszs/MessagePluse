package com.messagepulse.core.event;

import com.messagepulse.core.dto.MessageContent;
import com.messagepulse.core.dto.RecipientInfo;
import com.messagepulse.core.dto.RoutingConfig;
import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.enums.Priority;
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
public class MessageEvent {

    private String messageId;
    private String tenantId;
    private String channelType;
    private Priority priority;
    private MessageStatus status;
    private List<RecipientInfo> recipients;
    private MessageContent content;
    private RoutingConfig routingConfig;
    private Integer attemptCount;
    private LocalDateTime timestamp;
}
