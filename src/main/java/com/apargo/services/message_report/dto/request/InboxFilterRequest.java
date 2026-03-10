package com.apargo.services.message_report.dto.request;

import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class InboxFilterRequest {

    // ── From headers (set by controller, not the client) ─────────────────
    private Long   organizationId;   // from X-Organization-Id header
    private Long   userId;           // from X-User-Id header

    // ── Required query param ──────────────────────────────────────────────
    private Long   projectId;

    // ── Pagination ────────────────────────────────────────────────────────
    private String cursor;
    private int    size = 20;

    // ── Optional filters ─────────────────────────────────────────────────
    private ConversationStatus status;
    private AssignedType       assignedType;
    private Long               assignedId;
    private Boolean            unreadOnly;
    private Boolean            activeSession;
    private String             search;

    // ── Date range ────────────────────────────────────────────────────────
    private Instant fromDate;
    private Instant toDate;
}