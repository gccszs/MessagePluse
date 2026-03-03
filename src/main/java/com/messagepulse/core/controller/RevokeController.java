package com.messagepulse.core.controller;

import com.messagepulse.core.engine.revoke.RevokeEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class RevokeController {

    private final RevokeEngine revokeEngine;

    @PostMapping("/{messageId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeMessage(
            @PathVariable String messageId,
            @RequestHeader("X-Tenant-Id") String tenantId
    ) {
        log.info("Revoke request for message: {}, tenant: {}", messageId, tenantId);

        revokeEngine.revoke(messageId, tenantId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "message", "Message revoked successfully"
        ));
    }
}
