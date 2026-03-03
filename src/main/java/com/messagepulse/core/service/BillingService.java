package com.messagepulse.core.service;

import com.messagepulse.core.entity.BillingRecord;
import com.messagepulse.core.repository.BillingRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingService {

    private final BillingRecordRepository billingRecordRepository;

    public BillingService(BillingRecordRepository billingRecordRepository) {
        this.billingRecordRepository = billingRecordRepository;
    }

    @Transactional
    public BillingRecord recordBilling(String tenantId, String messageId, String channelType, BigDecimal cost) {
        BillingRecord record = BillingRecord.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .messageId(messageId)
                .channelType(channelType)
                .cost(cost)
                .billingTime(LocalDateTime.now())
                .build();

        return billingRecordRepository.save(record);
    }

    public List<BillingRecord> getRecords(String tenantId, LocalDateTime start, LocalDateTime end) {
        return billingRecordRepository.findByTenantIdAndBillingTimeBetween(tenantId, start, end);
    }

    public Map<String, Object> getStats(String tenantId, LocalDateTime start, LocalDateTime end) {
        List<BillingRecord> records = billingRecordRepository.findByTenantIdAndBillingTimeBetween(tenantId, start, end);

        BigDecimal totalCost = records.stream()
                .map(BillingRecord::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> countByChannel = new LinkedHashMap<>();
        Map<String, BigDecimal> costByChannel = new LinkedHashMap<>();
        for (BillingRecord record : records) {
            countByChannel.merge(record.getChannelType(), 1L, Long::sum);
            costByChannel.merge(record.getChannelType(), record.getCost(), BigDecimal::add);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("tenantId", tenantId);
        stats.put("totalMessages", records.size());
        stats.put("totalCost", totalCost);
        stats.put("countByChannel", countByChannel);
        stats.put("costByChannel", costByChannel);
        stats.put("startTime", start.toString());
        stats.put("endTime", end.toString());

        return stats;
    }
}
