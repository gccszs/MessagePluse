package com.messagepulse.core.engine.revoke;

public interface RevokeEngine {

    void revoke(String messageId, String tenantId);
}
