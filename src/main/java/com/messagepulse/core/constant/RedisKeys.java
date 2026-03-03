package com.messagepulse.core.constant;

public final class RedisKeys {

    private RedisKeys() {}

    public static final String PREFIX = "mp:";

    public static final String MESSAGE_DEDUP = PREFIX + "dedup:";
    public static final String MESSAGE_STATUS = PREFIX + "msg:status:";
    public static final String RATE_LIMIT = PREFIX + "ratelimit:";
    public static final String API_KEY = PREFIX + "apikey:";
    public static final String SKILL_INSTANCE = PREFIX + "skill:instance:";
    public static final String SKILL_HEARTBEAT = PREFIX + "skill:heartbeat:";
    public static final String ROUTING_CACHE = PREFIX + "routing:";
    public static final String TENANT_CONFIG = PREFIX + "tenant:config:";
}
