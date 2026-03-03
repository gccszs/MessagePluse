package com.messagepulse.core.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthIndicator extends AbstractHealthIndicator {

    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            if ("PONG".equals(pong)) {
                RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
                builder.up()
                        .withDetail("connection", "ok")
                        .withDetail("ping", pong);
            } else {
                builder.down()
                        .withDetail("ping", pong);
            }
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            builder.down()
                    .withDetail("error", e.getMessage());
        }
    }
}
