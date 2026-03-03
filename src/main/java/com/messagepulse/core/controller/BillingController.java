package com.messagepulse.core.controller;

import com.messagepulse.core.entity.BillingRecord;
import com.messagepulse.core.exception.AuthenticationException;
import com.messagepulse.core.security.ApiKeyAuthentication;
import com.messagepulse.core.security.RequireScope;
import com.messagepulse.core.service.BillingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/stats")
    @RequireScope("billing:read")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        String tenantId = extractTenantId();
        Map<String, Object> stats = billingService.getStats(tenantId, start, end);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/records")
    @RequireScope("billing:read")
    public ResponseEntity<List<BillingRecord>> getRecords(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        String tenantId = extractTenantId();
        List<BillingRecord> records = billingService.getRecords(tenantId, start, end);
        return ResponseEntity.ok(records);
    }

    private String extractTenantId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
            return apiKeyAuth.getTenantId();
        }
        throw new AuthenticationException("Unable to extract tenant ID from security context");
    }
}
