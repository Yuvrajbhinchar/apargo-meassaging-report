package com.apargo.services.message_report.projection;

import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import com.apargo.services.message_report.enums.MessageDirection;

import java.time.Instant;

/**
 * JPQL projection — maps directly from the single JOIN query.
 * Zero entity loading overhead, no N+1, no lazy init exceptions.
 */
public interface InboxProjection {

    // ── Conversation ──────────────────────────────────────────────────────
    Long           getConversationId();
    Long           getContactId();
    Long           getWabaAccountId();
    ConversationStatus  getStatus();
    AssignedType   getAssignedType();
    Long           getAssignedId();
    Instant        getLastMessageAt();
    MessageDirection getLastMessageDirection();
    String         getLastMessagePreview();
    Integer        getUnreadCount();
    Instant        getConversationOpenUntil();
    Instant        getLastInboundAt();
    Long           getLastMessageId();

    // ── Contact ───────────────────────────────────────────────────────────
    String         getContactName();
    String         getContactPhone();

    // ── Last message delivery ticks ───────────────────────────────────────
    Boolean        getIsSent();
    Boolean        getIsDelivered();
    Boolean        getIsRead();
    Boolean        getIsFailed();
}