package com.messagepulse.core.engine.dedup;

import com.messagepulse.core.config.DeduplicationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreeLevelDeduplicationEngineTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private TimeWindowBloomFilter bloomFilter;

    private DeduplicationConfig config;
    private ThreeLevelDeduplicationEngine engine;

    @BeforeEach
    void setUp() {
        config = new DeduplicationConfig();
        config.setEnabled(true);
        config.getCache().setMaxSize(1000);
        config.getCache().setExpireMinutes(10);
        config.getRedis().setKeyPrefix("mp:dedup:");
        config.getRedis().setTtlSeconds(3600);

        engine = new ThreeLevelDeduplicationEngine(redisTemplate, bloomFilter, config);
        engine.init();
    }

    @Test
    void testIsDuplicate_WhenDisabled_ReturnsFalse() {
        config.setEnabled(false);
        engine.init();

        boolean result = engine.isDuplicate("msg-001", "tenant-1");

        assertFalse(result);
        verifyNoInteractions(redisTemplate, bloomFilter);
    }

    @Test
    void testIsDuplicate_L1CacheHit_ReturnsTrue() {
        String messageId = "msg-001";
        String tenantId = "tenant-1";

        engine.isDuplicate(messageId, tenantId);
        boolean result = engine.isDuplicate(messageId, tenantId);

        assertTrue(result);
    }

    @Test
    void testIsDuplicate_L2RedisHit_ReturnsTrue() {
        String messageId = "msg-002";
        String tenantId = "tenant-1";

        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(1L);

        boolean result = engine.isDuplicate(messageId, tenantId);

        assertTrue(result);
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("mp:dedup:tenant-1:msg-002")),
                eq("tenant-1:msg-002"),
                eq("3600")
        );
    }

    @Test
    void testIsDuplicate_L3BloomFilterHit_ReturnsTrue() {
        String messageId = "msg-003";
        String tenantId = "tenant-1";

        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(0L);
        when(bloomFilter.mightContain("tenant-1:msg-003")).thenReturn(true);

        boolean result = engine.isDuplicate(messageId, tenantId);

        assertTrue(result);
        verify(bloomFilter).mightContain("tenant-1:msg-003");
    }

    @Test
    void testIsDuplicate_NoHit_ReturnsFalseAndMarkAsProcessed() {
        String messageId = "msg-004";
        String tenantId = "tenant-1";

        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(0L);
        when(bloomFilter.mightContain("tenant-1:msg-004")).thenReturn(false);

        boolean result = engine.isDuplicate(messageId, tenantId);

        assertFalse(result);
        verify(bloomFilter).put("tenant-1:msg-004");
    }

    @Test
    void testIsDuplicate_RedisException_ContinuesWithBloomFilter() {
        String messageId = "msg-005";
        String tenantId = "tenant-1";

        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenThrow(new RuntimeException("Redis connection failed"));
        when(bloomFilter.mightContain("tenant-1:msg-005")).thenReturn(false);

        boolean result = engine.isDuplicate(messageId, tenantId);

        assertFalse(result);
        verify(bloomFilter).mightContain("tenant-1:msg-005");
        verify(bloomFilter).put("tenant-1:msg-005");
    }
}
