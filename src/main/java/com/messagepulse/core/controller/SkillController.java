package com.messagepulse.core.controller;

import com.messagepulse.core.entity.SkillConfig;
import com.messagepulse.core.exception.AuthenticationException;
import com.messagepulse.core.security.ApiKeyAuthentication;
import com.messagepulse.core.security.RequireScope;
import com.messagepulse.core.service.SkillManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillManagementService skillManagementService;

    public SkillController(SkillManagementService skillManagementService) {
        this.skillManagementService = skillManagementService;
    }

    @GetMapping
    @RequireScope("skill:read")
    public ResponseEntity<List<SkillConfig>> listSkills(@RequestParam(required = false) String channelType) {
        String tenantId = extractTenantId();
        List<SkillConfig> skills = skillManagementService.discoverSkills(tenantId, channelType);
        return ResponseEntity.ok(skills);
    }

    @PostMapping("/{skillConfigId}/enable")
    @RequireScope("skill:manage")
    public ResponseEntity<Map<String, String>> enableSkill(@PathVariable String skillConfigId) {
        skillManagementService.enableSkill(skillConfigId);
        return ResponseEntity.ok(Map.of("status", "enabled", "skillConfigId", skillConfigId));
    }

    @PostMapping("/{skillConfigId}/disable")
    @RequireScope("skill:manage")
    public ResponseEntity<Map<String, String>> disableSkill(@PathVariable String skillConfigId) {
        skillManagementService.disableSkill(skillConfigId);
        return ResponseEntity.ok(Map.of("status", "disabled", "skillConfigId", skillConfigId));
    }

    @PostMapping("/heartbeat")
    @RequireScope("skill:heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat(@RequestBody Map<String, String> request) {
        String instanceId = request.get("instanceId");
        skillManagementService.updateHeartbeat(instanceId);
        return ResponseEntity.ok(Map.of("status", "ok", "instanceId", instanceId));
    }

    private String extractTenantId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
            return apiKeyAuth.getTenantId();
        }
        throw new AuthenticationException("Unable to extract tenant ID from security context");
    }
}
