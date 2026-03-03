package com.messagepulse.core.service;

import com.messagepulse.core.constant.RedisKeys;
import com.messagepulse.core.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;

@Service
public class RateLimiterService {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private DefaultRedisScript<Long> rateLimitScript;

    @PostConstruct
    public void init() {
        if (redisTemplate != null) {
            rateLimitScript = new DefaultRedisScript<>();
            rateLimitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/token-bucket.lua")));
            rateLimitScript.setResultType(Long.class);
        }
    }

    public void checkApiKeyRateLimit(String apiKeyId, int rateLimit) {
        checkRateLimit(RedisKeys.RATE_LIMIT + "apikey:" + apiKeyId, rateLimit, 1);
    }

    public void checkTenantRateLimit(String tenantId, int rateLimit) {
        checkRateLimit(RedisKeys.RATE_LIMIT + "tenant:" + tenantId, rateLimit, 1);
    }

    public void checkChannelRateLimit(String tenantId, String channelType, int rateLimit) {
        checkRateLimit(RedisKeys.RATE_LIMIT + "channel:" + tenantId + ":" + channelType, rateLimit, 1);
    }

    private void checkRateLimit(String key, int maxTokens, int requested) {
        if (redisTemplate == null) {
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        double refillRate = maxTokens / 60.0;

        Long allowed = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(maxTokens),
                String.valueOf(refillRate),
                String.valueOf(requested),
                String.valueOf(now)
        );

        if (allowed == null || allowed == 0) {
            throw new RateLimitExceededException(key);
        }
    }
}
