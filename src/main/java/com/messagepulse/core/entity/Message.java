package com.messagepulse.core.entity;

import com.messagepulse.core.enums.MessageStatus;
import com.messagepulse.core.enums.Priority;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_tenant_id", columnList = "tenantId"),
        @Index(name = "idx_messages_message_id", columnList = "messageId"),
        @Index(name = "idx_messages_status", columnList = "status"),
        @Index(name = "idx_messages_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, unique = true, length = 64)
    private String messageId;

    @Column(nullable = false, length = 32)
    private String channelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Priority priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String routingConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = MessageStatus.PENDING;
        }
        if (priority == null) {
            priority = Priority.NORMAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
