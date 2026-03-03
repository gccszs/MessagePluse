package com.messagepulse.core.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            String clusterId = clusterResult.clusterId().get(5, TimeUnit.SECONDS);
            int nodeCount = clusterResult.nodes().get(5, TimeUnit.SECONDS).size();

            builder.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodeCount", nodeCount);
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            builder.down()
                    .withDetail("error", e.getMessage());
        }
    }
}
