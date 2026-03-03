package com.messagepulse.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "skill_instances", indexes = {
        @Index(name = "idx_skill_instances_config_id", columnList = "skillConfigId"),
        @Index(name = "idx_skill_instances_instance_id", columnList = "instanceId"),
        @Index(name = "idx_skill_instances_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillInstance {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String skillConfigId;

    @Column(nullable = false, unique = true, length = 64)
    private String instanceId;

    @Column(nullable = false, length = 256)
    private String endpoint;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "INACTIVE";

    private LocalDateTime lastHeartbeat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String metadata;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
