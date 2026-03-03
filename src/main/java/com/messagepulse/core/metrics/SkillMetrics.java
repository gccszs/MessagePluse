package com.messagepulse.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class SkillMetrics {

    private final MeterRegistry registry;
    private final AtomicLong activeSkillInstances = new AtomicLong(0);

    public SkillMetrics(MeterRegistry registry) {
        this.registry = registry;
        initMetrics();
    }

    private void initMetrics() {
        Gauge.builder("messagepulse.skill.instances.active", activeSkillInstances, AtomicLong::get)
                .description("Number of active Skill instances")
                .register(registry);
    }

    public void recordSendResult(String skillType, String status) {
        Counter.builder("messagepulse.skill.send")
                .tag("skill_type", skillType)
                .tag("status", status)
                .description("Skill send results")
                .register(registry)
                .increment();
    }

    public void recordHeartbeatLatency(String skillType, long latencyMs) {
        DistributionSummary.builder("messagepulse.skill.heartbeat.latency")
                .tag("skill_type", skillType)
                .description("Skill heartbeat latency in milliseconds")
                .baseUnit("milliseconds")
                .register(registry)
                .record(latencyMs);
    }

    public void updateActiveInstances(long count) {
        activeSkillInstances.set(count);
    }

    public void incrementActiveInstances() {
        activeSkillInstances.incrementAndGet();
    }

    public void decrementActiveInstances() {
        activeSkillInstances.decrementAndGet();
    }
}
