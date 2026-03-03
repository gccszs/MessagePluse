package com.messagepulse.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "messagepulse.deduplication")
public class DeduplicationConfig {

    private boolean enabled = true;

    private Cache cache = new Cache();

    private Redis redis = new Redis();

    private BloomFilter bloomFilter = new BloomFilter();

    @Data
    public static class Cache {
        private int maxSize = 10000;
        private int expireMinutes = 10;
    }

    @Data
    public static class Redis {
        private String keyPrefix = "mp:dedup:";
        private int ttlSeconds = 3600;
    }

    @Data
    public static class BloomFilter {
        private int expectedInsertions = 1000000;
        private double falsePositiveProbability = 0.01;
        private String rotationCron = "0 0 * * * ?";
    }
}
