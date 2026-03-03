package com.messagepulse.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "billing_records", indexes = {
        @Index(name = "idx_billing_records_tenant_id", columnList = "tenantId"),
        @Index(name = "idx_billing_records_message_id", columnList = "messageId"),
        @Index(name = "idx_billing_records_billing_time", columnList = "billingTime")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 64)
    private String messageId;

    @Column(nullable = false, length = 32)
    private String channelType;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal cost;

    @Column(nullable = false)
    private LocalDateTime billingTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (billingTime == null) {
            billingTime = LocalDateTime.now();
        }
    }
}
