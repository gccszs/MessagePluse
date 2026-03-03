package com.messagepulse.core.config;

import com.messagepulse.core.constant.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final short REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic messageSendTopic() {
        return TopicBuilder.name(KafkaTopics.MESSAGE_SEND)
                .partitions(6)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic messageStatusTopic() {
        return TopicBuilder.name(KafkaTopics.MESSAGE_STATUS)
                .partitions(3)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic messageReceiptTopic() {
        return TopicBuilder.name(KafkaTopics.MESSAGE_RECEIPT)
                .partitions(6)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic messageRetryTopic() {
        return TopicBuilder.name(KafkaTopics.MESSAGE_RETRY)
                .partitions(3)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic messageDlqTopic() {
        return TopicBuilder.name(KafkaTopics.MESSAGE_DLQ)
                .partitions(2)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic skillHeartbeatTopic() {
        return TopicBuilder.name(KafkaTopics.SKILL_HEARTBEAT)
                .partitions(2)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic billingEventTopic() {
        return TopicBuilder.name(KafkaTopics.BILLING_EVENT)
                .partitions(3)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
