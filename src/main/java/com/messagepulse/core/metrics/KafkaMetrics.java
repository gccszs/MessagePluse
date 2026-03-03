package com.messagepulse.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaMetrics {

    private final MeterRegistry registry;

    public KafkaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordProduce(String topic, String status) {
        Counter.builder("messagepulse.kafka.produce")
                .tag("topic", topic)
                .tag("status", status)
                .description("Kafka messages produced")
                .register(registry)
                .increment();
    }

    public void recordConsume(String topic, String status) {
        Counter.builder("messagepulse.kafka.consume")
                .tag("topic", topic)
                .tag("status", status)
                .description("Kafka messages consumed")
                .register(registry)
                .increment();
    }

    public void recordProduceLatency(String topic, long latencyMs) {
        DistributionSummary.builder("messagepulse.kafka.produce.latency")
                .tag("topic", topic)
                .description("Kafka produce latency in milliseconds")
                .baseUnit("milliseconds")
                .register(registry)
                .record(latencyMs);
    }

    public void recordConsumeLatency(String topic, long latencyMs) {
        DistributionSummary.builder("messagepulse.kafka.consume.latency")
                .tag("topic", topic)
                .description("Kafka consume latency in milliseconds")
                .baseUnit("milliseconds")
                .register(registry)
                .record(latencyMs);
    }
}
