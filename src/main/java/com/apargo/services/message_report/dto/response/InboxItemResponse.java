package com.apargo.services.message_report.dto.response;

import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import com.apargo.services.message_report.enums.MessageDirection;
import com.apargo.services.message_report.projection.InboxProjection;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class InboxItemResponse {

    // ── Conversation identity ──────────────────────────────────────────────
    private final Long            conversationId;
    private final Long            contactId;
    private final Long            wabaAccountId;
    private final ConversationStatus status;

    // ── Contact info ───────────────────────────────────────────────────────
    private final String          contactName;
    private final String          contactPhone;

    // ── Last message snapshot ──────────────────────────────────────────────
    private final String          lastMessagePreview;
    private final Instant         lastMessageAt;
    private final MessageDirection lastMessageDirection;
    private final TickStatus      tickStatus;

    // ── Unread ────────────────────────────────────────────────────────────
    private final int             unreadCount;

    // ── Session window ────────────────────────────────────────────────────
    private final Instant         conversationOpenUntil;
    private final boolean         isSessionActive;       // openUntil > now
    private final Long            sessionRemainingMs;    // ms left, null if expired

    // ── Assignment ────────────────────────────────────────────────────────
    private final AssignedType    assignedType;
    private final Long            assignedId;

    // ── Factory ───────────────────────────────────────────────────────────

    public static InboxItemResponse from(InboxProjection p) {
        Instant now   = Instant.now();
        Instant until = p.getConversationOpenUntil();

        boolean active       = until != null && until.isAfter(now);
        Long    remainingMs  = active ? until.toEpochMilli() - now.toEpochMilli() : null;

        return InboxItemResponse.builder()
                .conversationId(p.getConversationId())
                .contactId(p.getContactId())
                .wabaAccountId(p.getWabaAccountId())
                .status(p.getStatus())
                .contactName(p.getContactName())
                .contactPhone(p.getContactPhone())
                .lastMessagePreview(p.getLastMessagePreview())
                .lastMessageAt(p.getLastMessageAt())
                .lastMessageDirection(p.getLastMessageDirection())
                .tickStatus(TickStatus.builder()
                        .isSent(Boolean.TRUE.equals(p.getIsSent()))
                        .isDelivered(Boolean.TRUE.equals(p.getIsDelivered()))
                        .isRead(Boolean.TRUE.equals(p.getIsRead()))
                        .isFailed(Boolean.TRUE.equals(p.getIsFailed()))
                        .build())
                .unreadCount(p.getUnreadCount() != null ? p.getUnreadCount() : 0)
                .conversationOpenUntil(until)
                .isSessionActive(active)
                .sessionRemainingMs(remainingMs)
                .assignedType(p.getAssignedType())
                .assignedId(p.getAssignedId())
                .build();
    }
}