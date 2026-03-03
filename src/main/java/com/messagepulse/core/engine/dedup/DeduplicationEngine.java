package com.messagepulse.core.engine.dedup;

public interface DeduplicationEngine {

    boolean isDuplicate(String messageId, String tenantId);
}
