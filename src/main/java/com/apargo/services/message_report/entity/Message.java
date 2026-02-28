package com.apargo.services.message_report.entity;

import com.apargo.services.message_report.enums.CreatedByType;
import com.apargo.services.message_report.enums.MessageDirection;
import com.apargo.services.message_report.enums.MessageStatus;
import com.apargo.services.message_report.enums.MessageType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_conversation_time", columnList = "conversation_id,created_at"),
        @Index(name = "idx_project_time",      columnList = "project_id,created_at"),
        @Index(name = "idx_contact_time",      columnList = "contact_id,created_at"),
        @Index(name = "idx_provider_msg",      columnList = "provider_message_id"),
        @Index(name = "idx_campaign",          columnList = "campaign_id")
})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_message_conversation"))
    private Conversation conversation;

    @Column(name = "waba_account_id", nullable = false)
    private Long wabaAccountId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", foreignKey = @ForeignKey(name = "fk_message_campaign"))
    private BroadcastCampaign campaign;

    // ── columnDefinition values MUST match DB EXACTLY (UPPERCASE) ────────

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false,
            columnDefinition = "ENUM('INBOUND','OUTBOUND')")
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false,
            columnDefinition = "ENUM('TEXT','IMAGE','VIDEO','AUDIO','DOCUMENT','TEMPLATE','INTERACTIVE','REACTION','SYSTEM')")
    private MessageType messageType;

    @Column(name = "template_name", length = 200)
    private String templateName;

    @Column(name = "template_language", length = 10)
    private String templateLanguage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_vars", columnDefinition = "JSON")
    private String templateVars;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "media_asset_id")
    private Long mediaAssetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "JSON")
    private String payload;


    @Column(name = "provider_message_id", length = 150)
    private String providerMessageId;


    @Enumerated(EnumType.STRING)
    @Column(name = "status",
            columnDefinition = "ENUM('QUEUED','PROCESSING','SENT','DELIVERED','READ','FAILED','REJECTED','EXPIRED')")
    private MessageStatus status = MessageStatus.QUEUED;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_type",
            columnDefinition = "ENUM('USER','SYSTEM','AUTOMATION','CAMPAIGN')")
    private CreatedByType createdByType = CreatedByType.USER;

    @Column(name = "created_by_id")
    private Long createdById;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    // ── Relationships ─────────────────────────────────────────────────────

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL)
    private List<MessageStatusEvent> statusEvents;

    @OneToOne(mappedBy = "message", cascade = CascadeType.ALL)
    private MessageStatusRollup statusRollup;

    @OneToMany(mappedBy = "message")
    private List<BroadcastRecipient> broadcastRecipients;
}