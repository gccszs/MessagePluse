package com.messagepulse.core.util;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;

public class MessageIdGenerator {

    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private static final int WORKER_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    private static final MessageIdGenerator INSTANCE = new MessageIdGenerator(generateWorkerId());

    private MessageIdGenerator(long workerId) {
        this.workerId = workerId & ((1L << WORKER_ID_BITS) - 1);
    }

    public static MessageIdGenerator getInstance() {
        return INSTANCE;
    }

    public synchronized String nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            timestamp = lastTimestamp;
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        long id = ((timestamp - EPOCH) << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;

        return Long.toString(id);
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    private static long generateWorkerId() {
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                }
            }
            return sb.toString().hashCode();
        } catch (Exception e) {
            return new SecureRandom().nextInt();
        }
    }
}
