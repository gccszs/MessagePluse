package com.messagepulse.core.kafka;

import com.messagepulse.core.constant.KafkaTopics;
import com.messagepulse.core.event.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProducer {

    private final KafkaTemplate<String, MessageEvent> kafkaTemplate;

    public void send(MessageEvent event) {
        String messageId = event.getMessageId();
        log.info("Sending message event to Kafka: messageId={}, topic={}", messageId, KafkaTopics.MESSAGE_SEND);

        kafkaTemplate.send(KafkaTopics.MESSAGE_SEND, messageId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send message event: messageId={}", messageId, ex);
                    } else {
                        log.info("Message event sent successfully: messageId={}, partition={}, offset={}",
                                messageId, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
