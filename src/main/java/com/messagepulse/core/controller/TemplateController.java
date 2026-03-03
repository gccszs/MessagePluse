package com.messagepulse.core.controller;

import com.messagepulse.core.entity.MessageTemplate;
import com.messagepulse.core.security.ApiKeyAuthentication;
import com.messagepulse.core.security.RequireScope;
import com.messagepulse.core.service.TemplateService;
import com.messagepulse.core.exception.AuthenticationException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    @RequireScope("template:write")
    public ResponseEntity<MessageTemplate> createTemplate(@Valid @RequestBody MessageTemplate template) {
        String tenantId = extractTenantId();
        template.setTenantId(tenantId);
        MessageTemplate created = templateService.createTemplate(template);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    @RequireScope("template:read")
    public ResponseEntity<MessageTemplate> getTemplate(@PathVariable String id) {
        MessageTemplate template = templateService.getTemplate(id);
        return ResponseEntity.ok(template);
    }

    @GetMapping
    @RequireScope("template:read")
    public ResponseEntity<List<MessageTemplate>> listTemplates(@RequestParam(required = false) String channelType) {
        String tenantId = extractTenantId();
        List<MessageTemplate> templates = templateService.listTemplates(tenantId, channelType);
        return ResponseEntity.ok(templates);
    }

    @PutMapping("/{id}")
    @RequireScope("template:write")
    public ResponseEntity<MessageTemplate> updateTemplate(@PathVariable String id, @Valid @RequestBody MessageTemplate template) {
        MessageTemplate updated = templateService.updateTemplate(id, template);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @RequireScope("template:write")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    private String extractTenantId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
            return apiKeyAuth.getTenantId();
        }
        throw new AuthenticationException("Unable to extract tenant ID from security context");
    }
}
