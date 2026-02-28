package com.apargo.services.message_report.entity;

import java.time.Instant;

import com.apargo.services.message_report.enums.RecipientState;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "broadcast_recipients", indexes = {
        @Index(name = "idx_pick", columnList = "campaign_id,state,next_run_at,id")
})
public class BroadcastRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_recipient_campaign"))
    private BroadcastCampaign campaign;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", nullable = false, columnDefinition = "JSON")
    private String requestPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "state",
            columnDefinition = "ENUM('PENDING','LOCKED','SENT','FAILED','SKIPPED','CANCELLED')")
    private RecipientState state = RecipientState.PENDING;

    @Column(name = "attempts", columnDefinition = "TINYINT UNSIGNED DEFAULT 0")
    private Byte attempts = 0;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "locked_by", length = 80)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", foreignKey = @ForeignKey(name = "fk_recipient_message"))
    private Message message;

    @Column(name = "provider_message_id", length = 150)
    private String providerMessageId;

    @Column(name = "fail_code", length = 40)
    private String failCode;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (nextRunAt == null) {
            nextRunAt = Instant.now();
        }
    }
}