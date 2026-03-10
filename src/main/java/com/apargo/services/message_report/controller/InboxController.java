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

@RestController
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    // ══════════════════════════════════════════════════════════════════════
    //  INBOX — data + count
    //  GET /api/chats/inbox
    //  Headers: X-Organization-Id (required), X-User-Id (optional)
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/chats/inbox")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getInbox(
            @RequestHeader("X-Organization-Id")                    Long               organizationId,
            @RequestHeader(value = "X-User-Id", required = false)  Long               userId,
            @RequestParam                                          Long               projectId,
            @RequestParam(required = false)                        String             cursor,
            @RequestParam(defaultValue = "20")                     int                size,
            @RequestParam(required = false)                        AssignedType       assignedType,
            @RequestParam(required = false)                        Long               assignedId,
            @RequestParam(required = false)                        Boolean            unreadOnly,
            @RequestParam(required = false)                        Boolean            activeSession,
            @RequestParam(required = false)                        String             search,
            @RequestParam(required = false)                        String             fromDate,
            @RequestParam(required = false)                        String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getInbox(buildRequest(
                organizationId, userId,
                projectId, cursor, size, null,
                assignedType, assignedId,
                unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INBOX — data only
    //  GET /api/chats/inbox/data
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/chats/inbox/data")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getInboxDataOnly(
            @RequestHeader("X-Organization-Id")                    Long               organizationId,
            @RequestHeader(value = "X-User-Id", required = false)  Long               userId,
            @RequestParam                                          Long               projectId,
            @RequestParam(required = false)                        String             cursor,
            @RequestParam(defaultValue = "20")                     int                size,
            @RequestParam(required = false)                        AssignedType       assignedType,
            @RequestParam(required = false)                        Long               assignedId,
            @RequestParam(required = false)                        Boolean            unreadOnly,
            @RequestParam(required = false)                        Boolean            activeSession,
            @RequestParam(required = false)                        String             search,
            @RequestParam(required = false)                        String             fromDate,
            @RequestParam(required = false)                        String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getInboxDataOnly(buildRequest(
                organizationId, userId,
                projectId, cursor, size, null,
                assignedType, assignedId,
                unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INBOX — count only
    //  GET /api/chats/inbox/count
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/chats/inbox/count")
    public ResponseEntity<ConversationCountResponse> getInboxCount(
            @RequestHeader("X-Organization-Id")                    Long               organizationId,
            @RequestHeader(value = "X-User-Id", required = false)  Long               userId,
            @RequestParam                                          Long               projectId,
            @RequestParam(required = false)                        AssignedType       assignedType,
            @RequestParam(required = false)                        Long               assignedId,
            @RequestParam(required = false)                        Boolean            unreadOnly,
            @RequestParam(required = false)                        Boolean            activeSession,
            @RequestParam(required = false)                        String             search,
            @RequestParam(required = false)                        String             fromDate,
            @RequestParam(required = false)                        String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getInboxCount(buildRequest(
                organizationId, userId,
                projectId, null, 20, ConversationStatus.OPEN,
                assignedType, assignedId,
                unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGE HISTORY — data + count
    //  GET /api/v1/get-messages-history
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/get-messages-history")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getMessageHistory(
            @RequestHeader("X-Organization-Id")                    Long               organizationId,
            @RequestHeader(value = "X-User-Id", required = false)  Long               userId,
            @RequestParam                                          Long               projectId,
            @RequestParam(required = false)                        String             cursor,
            @RequestParam(defaultValue = "20")                     int                size,
            @RequestParam(required = false)                        ConversationStatus status,
            @RequestParam(required = false)                        AssignedType       assignedType,
            @RequestParam(required = false)                        Long               assignedId,
            @RequestParam(required = false)                        Boolean            unreadOnly,
            @RequestParam(required = false)                        Boolean            activeSession,
            @RequestParam(required = false)                        String             search,
            @RequestParam(required = false)                        String             fromDate,
            @RequestParam(required = false)                        String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getMessageHistory(buildRequest(
                organizationId, userId,
                projectId, cursor, size, status,
                assignedType, assignedId,
                unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGE HISTORY — data only
    //  GET /api/v1/get-messages-history/data
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/get-messages-history/data")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getMessageHistoryDataOnly(
            @RequestHeader("X-Organization-Id")                    Long               organizationId,
            @RequestHeader(value = "X-User-Id", required = false)  Long               userId,
            @RequestParam                                          Long               projectId,
            @RequestParam(required = false)                        String             cursor,
            @RequestParam(defaultValue = "20")                     int                size,
            @RequestParam(required = false)                        ConversationStatus status,
            @RequestParam(required = false)                        AssignedType       assignedType,
            @RequestParam(required = false)                        Long               assignedId,
            @RequestParam(required = false)                        Boolean            unreadOnly,
            @RequestParam(required = false)                        Boolean            activeSession,
            @RequestParam(required = false)                        String             search,
            @RequestParam(required = false)                        String             fromDate,
            @RequestParam(required = false)                        String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getMessageHistoryDataOnly(buildRequest(
                organizationId, userId,
                projectId, cursor, size, status,
                assignedType, assignedId,
                unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGE HISTORY — count only
    //  GET /api/v1/get-messages-history/count
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/get-messages-history/count")
    public ResponseEntity<ConversationCountResponse> getMessageHistoryCount(
            @RequestHeader("X-Organization-Id")                    Long               organizationId,
            @RequestHeader(value = "X-User-Id", required = false)  Long               userId,
            @RequestParam                                          Long               projectId,
            @RequestParam(required = false)                        ConversationStatus status,
            @RequestParam(required = false)                        AssignedType       assignedType,
            @RequestParam(required = false)                        Long               assignedId,
            @RequestParam(required = false)                        Boolean            unreadOnly,
            @RequestParam(required = false)                        Boolean            activeSession,
            @RequestParam(required = false)                        String             search,
            @RequestParam(required = false)                        String             fromDate,
            @RequestParam(required = false)                        String             toDate
    ) {
        return ResponseEntity.ok(inboxService.getMessageHistoryCount(buildRequest(
                organizationId, userId,
                projectId, null, 20, status,
                assignedType, assignedId,
                unreadOnly, activeSession, search,
                parseFromDate(fromDate), parseToDate(toDate)
        )));
    }

    // ── Date parsing ──────────────────────────────────────────────────────

    private Instant parseFromDate(String date) {
        if (date == null || date.isBlank()) return null;
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant parseToDate(String date) {
        if (date == null || date.isBlank()) return null;
        return LocalDate.parse(date).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
    }

    // ── Request builder ───────────────────────────────────────────────────

    private InboxFilterRequest buildRequest(
            Long               organizationId,
            Long               userId,
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
        req.setOrganizationId(organizationId);
        req.setUserId(userId);
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