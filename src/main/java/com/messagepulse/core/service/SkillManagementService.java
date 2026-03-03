package com.messagepulse.core.service;

import com.messagepulse.core.entity.SkillConfig;
import com.messagepulse.core.entity.SkillInstance;
import com.messagepulse.core.repository.SkillConfigRepository;
import com.messagepulse.core.repository.SkillInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SkillManagementService {

    private final SkillConfigRepository skillConfigRepository;
    private final SkillInstanceRepository skillInstanceRepository;

    public SkillManagementService(SkillConfigRepository skillConfigRepository,
                                 SkillInstanceRepository skillInstanceRepository) {
        this.skillConfigRepository = skillConfigRepository;
        this.skillInstanceRepository = skillInstanceRepository;
    }

    @Transactional
    public SkillInstance registerSkillInstance(String skillConfigId, String instanceId, String endpoint) {
        SkillInstance instance = SkillInstance.builder()
                .id(UUID.randomUUID().toString())
                .skillConfigId(skillConfigId)
                .instanceId(instanceId)
                .endpoint(endpoint)
                .status("ACTIVE")
                .lastHeartbeat(LocalDateTime.now())
                .build();

        return skillInstanceRepository.save(instance);
    }

    public List<SkillConfig> discoverSkills(String tenantId, String channelType) {
        if (channelType != null) {
            return skillConfigRepository.findByTenantIdAndChannelTypeAndIsEnabled(tenantId, channelType, true);
        }
        return skillConfigRepository.findByTenantIdAndIsEnabled(tenantId, true);
    }

    @Transactional
    public void updateHeartbeat(String instanceId) {
        SkillInstance instance = skillInstanceRepository.findByInstanceId(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Skill instance not found: " + instanceId));
        instance.setLastHeartbeat(LocalDateTime.now());
        instance.setStatus("ACTIVE");
        skillInstanceRepository.save(instance);
    }

    @Transactional
    public void enableSkill(String skillConfigId) {
        SkillConfig config = skillConfigRepository.findById(skillConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Skill config not found: " + skillConfigId));
        config.setIsEnabled(true);
        skillConfigRepository.save(config);
    }

    @Transactional
    public void disableSkill(String skillConfigId) {
        SkillConfig config = skillConfigRepository.findById(skillConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Skill config not found: " + skillConfigId));
        config.setIsEnabled(false);
        skillConfigRepository.save(config);
    }

    public List<SkillInstance> getActiveInstances(String skillConfigId) {
        return skillInstanceRepository.findBySkillConfigIdAndStatus(skillConfigId, "ACTIVE");
    }
}
