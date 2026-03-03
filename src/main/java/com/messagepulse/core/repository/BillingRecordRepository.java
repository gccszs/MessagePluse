package com.messagepulse.core.repository;

import com.messagepulse.core.entity.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecord, String> {

    List<BillingRecord> findByTenantIdAndBillingTimeBetween(String tenantId, LocalDateTime start, LocalDateTime end);

    List<BillingRecord> findByMessageId(String messageId);
}
