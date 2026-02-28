package com.apargo.services.message_report.dto.response;

import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Top-level response for GET /api/chats/conversation/{id}/messages
 * Bundles conversation meta + paginated messages.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationDetailResponse {

    // ── Conversation meta ─────────────────────────────────────────────────
    private final Long               conversationId;
    private final Long               contactId;
    private final String             contactName;
    private final String             contactPhone;
    private final ConversationStatus status;
    private final AssignedType       assignedType;
    private final Long               assignedId;
    private final Instant            conversationOpenUntil;
    private final boolean            isSessionActive;
    private final Long               sessionRemainingMs;

    // ── Messages page ─────────────────────────────────────────────────────
    private final CursorPageResponse<ChatMessageResponse> messages;
}