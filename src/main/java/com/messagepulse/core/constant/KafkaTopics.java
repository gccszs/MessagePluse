package com.messagepulse.core.constant;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String MESSAGE_SEND = "messagepulse.message.send";
    public static final String MESSAGE_STATUS = "messagepulse.message.status";
    public static final String MESSAGE_RECEIPT = "messagepulse.message.receipt";
    public static final String MESSAGE_RETRY = "messagepulse.message.retry";
    public static final String MESSAGE_DLQ = "messagepulse.message.dlq";
    public static final String SKILL_HEARTBEAT = "messagepulse.skill.heartbeat";
    public static final String BILLING_EVENT = "messagepulse.billing.event";
}
