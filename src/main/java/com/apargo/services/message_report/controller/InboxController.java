package com.apargo.services.message_report.controller;

import com.apargo.services.message_report.dto.request.InboxFilterRequest;
import com.apargo.services.message_report.dto.response.ConversationCountResponse;
import com.apargo.services.message_report.dto.response.CursorPageResponse;
import com.apargo.services.message_report.dto.response.InboxItemResponse;
import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import com.apargo.services.message_report.service.InboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * fromDate / toDate query params:
 *   Format : yyyy-MM-dd  (e.g. 2025-01-15)
 *   fromDate → start of that day 2025-01-15T00:00:00Z
 *   toDate   → end   of that day 2025-01-15T23:59:59Z
 *   Both optional. Omit both for no date filter.
 */
@RestController
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    // ══════════════════════════════════════════════════════════════════════
    //  INBOX — ORIGINAL (data + count)
    //  GET /api/chats/inbox?projectId=1&fromDate=2025-01-01&toDate=2025-03-31
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/chats/inbox")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getInbox(
            @RequestParam                      Long               projectId,
            @RequestParam(required = false)    String             cursor,
            @RequestParam(defaultValue = "20") int                size,
            @RequestParam(required = false)    AssignedType       assignedType,
            @RequestParam(required = false)    Long               assignedId,
            @RequestParam(required = false)    Boolean            unreadOnly,
            @RequestParam(required = false)    Boolean            activeSession,
            @RequestParam(required = false)    String             search,
            @RequestParam(required = false)    String             fromDate,
            @RequestParam(required = false)    String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getInbox(buildRequest(
                projectId, cursor, size, null,
                assignedType, assignedId, unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INBOX — DATA ONLY (no COUNT query)
    //  GET /api/chats/inbox/data?projectId=1&fromDate=2025-01-01&toDate=2025-03-31
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/chats/inbox/data")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getInboxDataOnly(
            @RequestParam                      Long               projectId,
            @RequestParam(required = false)    String             cursor,
            @RequestParam(defaultValue = "20") int                size,
            @RequestParam(required = false)    AssignedType       assignedType,
            @RequestParam(required = false)    Long               assignedId,
            @RequestParam(required = false)    Boolean            unreadOnly,
            @RequestParam(required = false)    Boolean            activeSession,
            @RequestParam(required = false)    String             search,
            @RequestParam(required = false)    String             fromDate,
            @RequestParam(required = false)    String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getInboxDataOnly(buildRequest(
                projectId, cursor, size, null,
                assignedType, assignedId, unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INBOX — COUNT ONLY
    //  GET /api/chats/inbox/count?projectId=1&fromDate=2025-01-01&toDate=2025-03-31
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/chats/inbox/count")
    public ResponseEntity<ConversationCountResponse> getInboxCount(
            @RequestParam                      Long               projectId,
            @RequestParam(required = false)    AssignedType       assignedType,
            @RequestParam(required = false)    Long               assignedId,
            @RequestParam(required = false)    Boolean            unreadOnly,
            @RequestParam(required = false)    Boolean            activeSession,
            @RequestParam(required = false)    String             search,
            @RequestParam(required = false)    String             fromDate,
            @RequestParam(required = false)    String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getInboxCount(buildRequest(
                projectId, null, 20, ConversationStatus.OPEN,
                assignedType, assignedId, unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGE HISTORY — ORIGINAL (data + count)
    //  GET /api/v1/get-messages-history?projectId=1&fromDate=2025-01-01
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/get-messages-history")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getMessageHistory(
            @RequestParam                      Long               projectId,
            @RequestParam(required = false)    String             cursor,
            @RequestParam(defaultValue = "20") int                size,
            @RequestParam(required = false)    ConversationStatus status,
            @RequestParam(required = false)    AssignedType       assignedType,
            @RequestParam(required = false)    Long               assignedId,
            @RequestParam(required = false)    Boolean            unreadOnly,
            @RequestParam(required = false)    Boolean            activeSession,
            @RequestParam(required = false)    String             search,
            @RequestParam(required = false)    String             fromDate,
            @RequestParam(required = false)    String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getMessageHistory(buildRequest(
                projectId, cursor, size, status,
                assignedType, assignedId, unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGE HISTORY — DATA ONLY (no COUNT query)
    //  GET /api/v1/get-messages-history/data?projectId=1&fromDate=2025-01-01
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/get-messages-history/data")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getMessageHistoryDataOnly(
            @RequestParam                      Long               projectId,
            @RequestParam(required = false)    String             cursor,
            @RequestParam(defaultValue = "20") int                size,
            @RequestParam(required = false)    ConversationStatus status,
            @RequestParam(required = false)    AssignedType       assignedType,
            @RequestParam(required = false)    Long               assignedId,
            @RequestParam(required = false)    Boolean            unreadOnly,
            @RequestParam(required = false)    Boolean            activeSession,
            @RequestParam(required = false)    String             search,
            @RequestParam(required = false)    String             fromDate,
            @RequestParam(required = false)    String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getMessageHistoryDataOnly(buildRequest(
                projectId, cursor, size, status,
                assignedType, assignedId, unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGE HISTORY — COUNT ONLY
    //  GET /api/v1/get-messages-history/count?projectId=1&fromDate=2025-01-01
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/get-messages-history/count")
    public ResponseEntity<ConversationCountResponse> getMessageHistoryCount(
            @RequestParam                      Long               projectId,
            @RequestParam(required = false)    ConversationStatus status,
            @RequestParam(required = false)    AssignedType       assignedType,
            @RequestParam(required = false)    Long               assignedId,
            @RequestParam(required = false)    Boolean            unreadOnly,
            @RequestParam(required = false)    Boolean            activeSession,
            @RequestParam(required = false)    String             search,
            @RequestParam(required = false)    String             fromDate,
            @RequestParam(required = false)    String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getMessageHistoryCount(buildRequest(
                projectId, null, 20, status,
                assignedType, assignedId, unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ── Date parsing ──────────────────────────────────────────────────────

    /** "yyyy-MM-dd" → 2025-01-15T00:00:00Z  (null if blank) */
    private Instant parseFromDate(String date) {
        if (date == null || date.isBlank()) return null;
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** "yyyy-MM-dd" → 2025-01-15T23:59:59Z  (null if blank) */
    private Instant parseToDate(String date) {
        if (date == null || date.isBlank()) return null;
        return LocalDate.parse(date).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
    }

    // ── Request builder ───────────────────────────────────────────────────

    private InboxFilterRequest buildRequest(
            Long               projectId,
            String             cursor,
            int                size,
            ConversationStatus status,
            AssignedType       assignedType,
            Long               assignedId,
            Boolean            unreadOnly,
            Boolean            activeSession,
            String             search,
            Instant            fromDate,
            Instant            toDate
    ) {
        InboxFilterRequest req = new InboxFilterRequest();
        req.setProjectId(projectId);
        req.setCursor(cursor);
        req.setSize(size);
        req.setStatus(status);
        req.setAssignedType(assignedType);
        req.setAssignedId(assignedId);
        req.setUnreadOnly(unreadOnly);
        req.setActiveSession(activeSession);
        req.setSearch(search);
        req.setFromDate(fromDate);
        req.setToDate(toDate);
        return req;
    }
}