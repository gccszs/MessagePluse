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
public class RecipientInfo {

    private String recipientId;
    private String recipientType;
    private String email;
    private String phone;
    private String userId;
    private Map<String, String> customFields;
}
