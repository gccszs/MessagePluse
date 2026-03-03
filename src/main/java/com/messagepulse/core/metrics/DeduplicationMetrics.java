package com.messagepulse.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class DeduplicationMetrics {

    private final MeterRegistry registry;
    private final AtomicLong bloomFilterSize = new AtomicLong(0);

    public DeduplicationMetrics(MeterRegistry registry) {
        this.registry = registry;
        initMetrics();
    }

    private void initMetrics() {
        Gauge.builder("messagepulse.dedup.bloomfilter.size", bloomFilterSize, AtomicLong::get)
                .description("Approximate bloom filter size")
                .register(registry);
    }

    public void recordDedupHit(String level) {
        Counter.builder("messagepulse.dedup.hits")
                .tag("level", level)
                .description("Deduplication hits by level (L1/L2/L3)")
                .register(registry)
                .increment();
    }

    public void recordDedupMiss() {
        Counter.builder("messagepulse.dedup.misses")
                .description("Deduplication misses")
                .register(registry)
                .increment();
    }

    public void updateBloomFilterSize(long size) {
        bloomFilterSize.set(size);
    }

    public void recordDedupCheck() {
        Counter.builder("messagepulse.dedup.checks")
                .description("Total deduplication checks")
                .register(registry)
                .increment();
    }
}
