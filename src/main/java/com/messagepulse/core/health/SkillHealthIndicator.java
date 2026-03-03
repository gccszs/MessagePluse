package com.messagepulse.core.health;

import com.messagepulse.core.entity.SkillInstance;
import com.messagepulse.core.repository.SkillInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillHealthIndicator extends AbstractHealthIndicator {

    private final SkillInstanceRepository skillInstanceRepository;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            List<SkillInstance> activeInstances = skillInstanceRepository.findByStatus("ACTIVE");
            long totalCount = skillInstanceRepository.count();

            if (!activeInstances.isEmpty()) {
                builder.up()
                        .withDetail("activeInstances", activeInstances.size())
                        .withDetail("totalInstances", totalCount);
            } else if (totalCount == 0) {
                builder.unknown()
                        .withDetail("message", "No skill instances registered")
                        .withDetail("totalInstances", 0);
            } else {
                builder.down()
                        .withDetail("message", "No active skill instances")
                        .withDetail("totalInstances", totalCount);
            }
        } catch (Exception e) {
            log.error("Skill health check failed", e);
            builder.down()
                    .withDetail("error", e.getMessage());
        }
    }
}
