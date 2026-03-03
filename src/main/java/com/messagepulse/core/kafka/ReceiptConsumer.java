package com.messagepulse.core.kafka;

import com.messagepulse.core.constant.KafkaTopics;
import com.messagepulse.core.event.MessageReceipt;
import com.messagepulse.core.service.ReceiptProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptConsumer {

    private final ReceiptProcessService receiptProcessService;

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_RECEIPT,
            groupId = "messagepulse-receipt"
    )
    public void consume(MessageReceipt receipt) {
        log.info("Received message receipt: messageId={}, channelType={}, deliveryStatus={}",
                receipt.getMessageId(), receipt.getChannelType(), receipt.getDeliveryStatus());

        try {
            receiptProcessService.processReceipt(receipt);
            log.info("Receipt processed successfully: messageId={}", receipt.getMessageId());
        } catch (Exception e) {
            log.error("Failed to process receipt: messageId={}", receipt.getMessageId(), e);
        }
    }
}
