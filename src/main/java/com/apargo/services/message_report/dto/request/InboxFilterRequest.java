package com.apargo.services.message_report.dto.request;

import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InboxFilterRequest {

    // ── Required ──────────────────────────────────────────────────────────
    private Long   projectId;

    // ── Pagination ────────────────────────────────────────────────────────
    private String cursor;           // null = first page
    private int    size = 20;        // default page size

    // ── Filters ───────────────────────────────────────────────────────────
    private ConversationStatus status;       // null = ALL (history), OPEN = inbox
    private AssignedType       assignedType; // null = all
    private Long               assignedId;  // filter by agent/team id
    private Boolean            unreadOnly;  // true = only unread
    private Boolean            activeSession; // true = only conversations with open 24h window
    private String             search;       // contact name/phone search (optional)
}