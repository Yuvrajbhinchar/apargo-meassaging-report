package com.apargo.services.message_report.entity;

import java.time.Instant;

import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import com.apargo.services.message_report.enums.MessageDirection;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_inbox",    columnList = "project_id,waba_account_id,last_message_at"),
        @Index(name = "idx_assigned", columnList = "project_id,assigned_type,assigned_id,last_message_at"),
        @Index(name = "idx_contact",  columnList = "contact_id")
})
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "waba_account_id", nullable = false)
    private Long wabaAccountId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    // ── columnDefinition MUST match DB exactly (UPPERCASE) ───────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('OPEN','CLOSED','ARCHIVED','BLOCKED')")
    private ConversationStatus status = ConversationStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_type", columnDefinition = "ENUM('UNASSIGNED','USER','TEAM')")
    private AssignedType assignedType = AssignedType.UNASSIGNED;

    @Column(name = "assigned_id")
    private Long assignedId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_message_direction", columnDefinition = "ENUM('INBOUND','OUTBOUND')")
    private MessageDirection lastMessageDirection;

    @Column(name = "last_message_preview", length = 300)
    private String lastMessagePreview;

    @Column(name = "unread_count", columnDefinition = "INT UNSIGNED DEFAULT 0")
    private Integer unreadCount = 0;

    @Column(name = "conversation_open_until")
    private Instant conversationOpenUntil;

    @Column(name = "last_inbound_at")
    private Instant lastInboundAt;

    @Column(name = "last_outbound_at")
    private Instant lastOutboundAt;

    @Column(name = "is_locked", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isLocked = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}