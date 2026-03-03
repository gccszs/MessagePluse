package com.messagepulse.core.engine.dedup;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
public class TimeWindowBloomFilter {

    private static final int EXPECTED_INSERTIONS = 1_000_000;
    private static final double FALSE_POSITIVE_PROBABILITY = 0.01;

    private BloomFilter<String> currentWindow;
    private BloomFilter<String> previousWindow;
    private LocalDateTime currentWindowStartTime;

    public TimeWindowBloomFilter() {
        this.currentWindow = createBloomFilter();
        this.previousWindow = createBloomFilter();
        this.currentWindowStartTime = LocalDateTime.now();
    }

    private BloomFilter<String> createBloomFilter() {
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FALSE_POSITIVE_PROBABILITY
        );
    }

    public synchronized boolean mightContain(String key) {
        return currentWindow.mightContain(key) || previousWindow.mightContain(key);
    }

    public synchronized void put(String key) {
        currentWindow.put(key);
    }

    public synchronized void rotate() {
        log.info("Rotating bloom filter window, previous window start time: {}", currentWindowStartTime);
        previousWindow = currentWindow;
        currentWindow = createBloomFilter();
        currentWindowStartTime = LocalDateTime.now();
    }

    public LocalDateTime getCurrentWindowStartTime() {
        return currentWindowStartTime;
    }
}
