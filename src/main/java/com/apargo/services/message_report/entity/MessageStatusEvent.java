package com.apargo.services.message_report.entity;

import java.time.Instant;

import com.apargo.services.message_report.enums.ProviderStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.ForeignKey;

@Data
@Entity
@Table(name = "message_status_events", indexes = {
        @Index(name = "idx_message_status", columnList = "message_id,provider_status"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class MessageStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false, foreignKey = @ForeignKey(name = "fk_status_event_message"))
    private Message message;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_status", nullable = false, columnDefinition = "ENUM('sent','delivered','read','failed')")
    private ProviderStatus providerStatus;

    @Column(name = "provider_timestamp")
    private Instant providerTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "JSON")
    private String rawPayload;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }


}