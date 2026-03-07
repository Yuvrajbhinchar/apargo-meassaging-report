package com.apargo.services.message_report.dto.request;

import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class InboxFilterRequest {

    // ── Required ──────────────────────────────────────────────────────────
    private Long   projectId;

    // ── Pagination ────────────────────────────────────────────────────────
    private String cursor;
    private int    size = 20;

    // ── Filters ───────────────────────────────────────────────────────────
    private ConversationStatus status;
    private AssignedType       assignedType;
    private Long               assignedId;
    private Boolean            unreadOnly;
    private Boolean            activeSession;
    private String             search;

    // ── Date range (filters on last_message_at) ───────────────────────────
    // Parsed from "yyyy-MM-dd" in the controller before reaching here.
    // fromDate = 2025-01-01T00:00:00Z  (start of day UTC)
    // toDate   = 2025-03-31T23:59:59Z  (end   of day UTC)
    // null = no bound applied
    private Instant fromDate;
    private Instant toDate;
}