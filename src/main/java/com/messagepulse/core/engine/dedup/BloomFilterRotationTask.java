package com.messagepulse.core.engine.dedup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterRotationTask {

    private final TimeWindowBloomFilter bloomFilter;

    @Scheduled(cron = "0 0 * * * ?")
    public void rotateBloomFilter() {
        log.info("Starting bloom filter rotation task");
        bloomFilter.rotate();
        log.info("Bloom filter rotation completed");
    }
}
