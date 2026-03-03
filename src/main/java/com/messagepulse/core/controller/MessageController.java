package com.messagepulse.core.controller;

import com.messagepulse.core.dto.request.SendMessageRequest;
import com.messagepulse.core.dto.response.SendMessageResponse;
import com.messagepulse.core.security.RequireScope;
import com.messagepulse.core.service.MessageSendService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageSendService messageSendService;

    public MessageController(MessageSendService messageSendService) {
        this.messageSendService = messageSendService;
    }

    @PostMapping
    @RequireScope("message:send")
    public ResponseEntity<SendMessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        SendMessageResponse response = messageSendService.sendMessage(request);
        return ResponseEntity.ok(response);
    }
}
