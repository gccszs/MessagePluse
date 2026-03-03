package com.messagepulse.core.entity;

import com.messagepulse.core.enums.DeliveryStatus;
import com.messagepulse.core.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_states", indexes = {
        @Index(name = "idx_message_states_message_id", columnList = "messageId"),
        @Index(name = "idx_message_states_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String messageId;

    @Column(nullable = false, length = 32)
    private String channelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private DeliveryStatus deliveryStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String receipt;

    @Column(length = 1024)
    private String errorMessage;

    @Column(nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
