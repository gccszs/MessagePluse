package com.messagepulse.core.repository;

import com.messagepulse.core.entity.SkillConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillConfigRepository extends JpaRepository<SkillConfig, String> {

    List<SkillConfig> findByTenantIdAndChannelTypeAndIsEnabled(String tenantId, String channelType, Boolean isEnabled);

    List<SkillConfig> findByTenantIdAndIsEnabled(String tenantId, Boolean isEnabled);
}
