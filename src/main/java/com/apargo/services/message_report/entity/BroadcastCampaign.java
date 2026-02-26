package com.apargo.services.message_report.entity;


import com.apargo.services.message_report.enums.CampaignStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


@Entity
@Table(
        name = "broadcast_campaigns",
        indexes = {
                @Index(name = "idx_campaign_project_status", columnList = "project_id, status"),
                @Index(name = "idx_campaign_scheduled",      columnList = "status, scheduled_at"),
                @Index(name = "idx_campaign_org",             columnList = "organization_id, project_id, created_at DESC")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "waba_account_id", nullable = false)
    private Long wabaAccountId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

   // @Convert(converter = PhoneNumberListConverter.class)
    @Column(name = "phone_numbers", nullable = false, columnDefinition = "JSON")
    private List<String> phoneNumbers;

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    // ── Processing config ────────────────────────────────────────────────

    @Column(name = "send_rate_per_sec")
    private Integer sendRatePerSec;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Byte maxAttempts = 3;

    // ── Stats (denormalized) ─────────────────────────────────────────────

    @Column(name = "total_recipients", nullable = false)
    @Builder.Default
    private Integer totalRecipients = 0;

    @Column(name = "sent_count", nullable = false)
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "delivered_count", nullable = false)
    @Builder.Default
    private Integer deliveredCount = 0;

    @Column(name = "read_count", nullable = false)
    @Builder.Default
    private Integer readCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    // ── Audit ────────────────────────────────────────────────────────────

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

}
