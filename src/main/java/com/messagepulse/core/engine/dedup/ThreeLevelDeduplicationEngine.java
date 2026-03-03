package com.messagepulse.core.engine.dedup;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.messagepulse.core.config.DeduplicationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class ThreeLevelDeduplicationEngine implements DeduplicationEngine {

    private final StringRedisTemplate redisTemplate;
    private final TimeWindowBloomFilter bloomFilter;
    private final DeduplicationConfig config;

    private Cache<String, Boolean> localCache;
    private DefaultRedisScript<Long> dedupScript;

    @PostConstruct
    public void init() {
        localCache = CacheBuilder.newBuilder()
                .maximumSize(config.getCache().getMaxSize())
                .expireAfterWrite(config.getCache().getExpireMinutes(), TimeUnit.MINUTES)
                .build();

        dedupScript = new DefaultRedisScript<>();
        dedupScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/dedup-check.lua")));
        dedupScript.setResultType(Long.class);

        log.info("ThreeLevelDeduplicationEngine initialized with config: {}", config);
    }

    @Override
    public boolean isDuplicate(String messageId, String tenantId) {
        if (!config.isEnabled()) {
            return false;
        }

        String key = buildKey(messageId, tenantId);

        if (checkLevel1(key)) {
            log.debug("L1 cache hit for key: {}", key);
            return true;
        }

        if (checkLevel2(key)) {
            log.debug("L2 Redis hit for key: {}", key);
            localCache.put(key, true);
            return true;
        }

        if (checkLevel3(key)) {
            log.debug("L3 BloomFilter hit for key: {}", key);
            return true;
        }

        markAsProcessed(key);
        return false;
    }

    private boolean checkLevel1(String key) {
        return localCache.getIfPresent(key) != null;
    }

    private boolean checkLevel2(String key) {
        try {
            String redisKey = config.getRedis().getKeyPrefix() + key;
            Long result = redisTemplate.execute(
                    dedupScript,
                    Collections.singletonList(redisKey),
                    key,
                    String.valueOf(config.getRedis().getTtlSeconds())
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Redis dedup check failed for key: {}", key, e);
            return false;
        }
    }

    private boolean checkLevel3(String key) {
        return bloomFilter.mightContain(key);
    }

    private void markAsProcessed(String key) {
        localCache.put(key, true);
        bloomFilter.put(key);
        log.debug("Marked key as processed: {}", key);
    }

    private String buildKey(String messageId, String tenantId) {
        return tenantId + ":" + messageId;
    }
}
