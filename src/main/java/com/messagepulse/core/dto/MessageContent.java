package com.messagepulse.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageContent {

    private String subject;
    private String body;
    private Map<String, Object> metadata;
    private Map<String, String> attachments;
}
