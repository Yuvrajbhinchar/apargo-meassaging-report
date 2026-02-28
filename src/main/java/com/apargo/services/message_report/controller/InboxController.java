package com.apargo.services.message_report.controller;

import com.apargo.services.message_report.dto.request.InboxFilterRequest;
import com.apargo.services.message_report.dto.response.CursorPageResponse;
import com.apargo.services.message_report.dto.response.InboxItemResponse;
import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import com.apargo.services.message_report.service.InboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    // ══════════════════════════════════════════════════════════════════════
    //  INBOX — open conversations only
    //  GET /api/chats/inbox?projectId=1&cursor=...&size=20&assignedType=USER
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/chats/inbox")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getInbox(
            @RequestParam                        Long projectId,
            @RequestParam(required = false)      String cursor,
            @RequestParam(defaultValue = "20")   int size,
            @RequestParam(required = false)      AssignedType assignedType,
            @RequestParam(required = false)      Long assignedId,
            @RequestParam(required = false)      Boolean unreadOnly,
            @RequestParam(required = false)      Boolean activeSession,
            @RequestParam(required = false)      String search
    ) {
        InboxFilterRequest req = buildRequest(
                projectId, cursor, size, null,
                assignedType, assignedId, unreadOnly, activeSession, search
        );
        return ResponseEntity.ok(inboxService.getInbox(req));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGE HISTORY — all conversations, optional status filter
    //  GET /api/v1/get-messages-history?projectId=1&status=CLOSED
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/get-messages-history")
    public ResponseEntity<CursorPageResponse<InboxItemResponse>> getMessageHistory(
            @RequestParam                        Long projectId,
            @RequestParam(required = false)      String cursor,
            @RequestParam(defaultValue = "20")   int size,
            @RequestParam(required = false)      ConversationStatus status,
            @RequestParam(required = false)      AssignedType assignedType,
            @RequestParam(required = false)      Long assignedId,
            @RequestParam(required = false)      Boolean unreadOnly,
            @RequestParam(required = false)      Boolean activeSession,
            @RequestParam(required = false)      String search
    ) {
        InboxFilterRequest req = buildRequest(
                projectId, cursor, size, status,
                assignedType, assignedId, unreadOnly, activeSession, search
        );
        return ResponseEntity.ok(inboxService.getMessageHistory(req));
    }

    // ── Builder helper ────────────────────────────────────────────────────

    private InboxFilterRequest buildRequest(
            Long projectId, String cursor, int size,
            ConversationStatus status,
            AssignedType assignedType, Long assignedId,
            Boolean unreadOnly, Boolean activeSession, String search
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
        return req;
    }
}