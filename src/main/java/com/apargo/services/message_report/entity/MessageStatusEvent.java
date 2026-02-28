package com.apargo.services.message_report.entity;

import java.time.Instant;

import com.apargo.services.message_report.enums.ProviderStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "message_status_events", indexes = {
        @Index(name = "idx_message_status", columnList = "message_id,provider_status"),
        @Index(name = "idx_created_at",     columnList = "created_at")
})
public class MessageStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_status_event_message"))
    private Message message;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_status", nullable = false,
            columnDefinition = "ENUM('SENT','DELIVERED','READ','FAILED')")
    private ProviderStatus providerStatus;

    @Column(name = "provider_timestamp")
    private Instant providerTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "JSON")
    private String rawPayload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}