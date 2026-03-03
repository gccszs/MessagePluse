package com.messagepulse.core.dto.request;

import com.messagepulse.core.dto.MessageContent;
import com.messagepulse.core.dto.RecipientInfo;
import com.messagepulse.core.dto.RoutingConfig;
import com.messagepulse.core.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequest {

    @NotBlank(message = "Message ID is required")
    private String messageId;

    @NotBlank(message = "Channel type is required")
    private String channelType;

    @NotNull(message = "Recipients are required")
    private List<RecipientInfo> recipients;

    @NotNull(message = "Content is required")
    private MessageContent content;

    private RoutingConfig routingConfig;

    @Builder.Default
    private Priority priority = Priority.NORMAL;

    private String templateId;
}
