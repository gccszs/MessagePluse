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
public class MessageMetrics {

    private final MeterRegistry registry;
    private final AtomicLong activeMessages = new AtomicLong(0);

    public MessageMetrics(MeterRegistry registry) {
        this.registry = registry;
        initMetrics();
    }

    private void initMetrics() {
        Gauge.builder("messagepulse.messages.active", activeMessages, AtomicLong::get)
                .description("Number of active messages")
                .register(registry);
    }

    public void recordMessageSent(String channelType, String status) {
        Counter.builder("messagepulse.messages.sent")
                .tag("channel", channelType)
                .tag("status", status)
                .description("Total messages sent")
                .register(registry)
                .increment();
    }

    public void recordSendLatency(String channelType, long latencyMs) {
        DistributionSummary.builder("messagepulse.messages.send.latency")
                .tag("channel", channelType)
                .description("Message send latency in milliseconds")
                .baseUnit("milliseconds")
                .register(registry)
                .record(latencyMs);
    }

    public void incrementActiveMessages() {
        activeMessages.incrementAndGet();
    }

    public void decrementActiveMessages() {
        activeMessages.decrementAndGet();
    }
}
