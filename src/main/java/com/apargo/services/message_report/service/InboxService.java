package com.apargo.services.message_report.service;

import com.apargo.services.message_report.dto.request.InboxFilterRequest;
import com.apargo.services.message_report.dto.response.CursorPageResponse;
import com.apargo.services.message_report.dto.response.CursorUtil;
import com.apargo.services.message_report.dto.response.InboxItemResponse;
import com.apargo.services.message_report.enums.AssignedType;
import com.apargo.services.message_report.enums.ConversationStatus;
import com.apargo.services.message_report.projection.InboxProjection;
import com.apargo.services.message_report.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InboxService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ConversationRepository conversationRepo;

    // ══════════════════════════════════════════════════════════════════════
    //  INBOX  — open conversations only (status forced to OPEN)
    // ══════════════════════════════════════════════════════════════════════

    public CursorPageResponse<InboxItemResponse> getInbox(InboxFilterRequest req) {
        req.setStatus(ConversationStatus.OPEN);
        return fetchPage(req);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGE HISTORY — all conversations; status optional filter
    // ══════════════════════════════════════════════════════════════════════

    public CursorPageResponse<InboxItemResponse> getMessageHistory(InboxFilterRequest req) {
        // status stays null (= all) unless caller passes e.g. ?status=CLOSED
        return fetchPage(req);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHARED CORE
    // ══════════════════════════════════════════════════════════════════════

    private CursorPageResponse<InboxItemResponse> fetchPage(InboxFilterRequest req) {

        int size = Math.min(req.getSize(), MAX_PAGE_SIZE);

        // Fetch size+1 to detect next page without a separate COUNT query on scroll
        Pageable limit = PageRequest.of(0, size + 1);

        boolean unreadOnly    = Boolean.TRUE.equals(req.getUnreadOnly());
        boolean activeSession = Boolean.TRUE.equals(req.getActiveSession());

        // Typed enums — Hibernate resolves bind type correctly even when null
        ConversationStatus status       = req.getStatus();        // null = no filter
        AssignedType       assignedType = req.getAssignedType();  // null = no filter
        String             search       = blankToNull(req.getSearch());

        List<InboxProjection> rows;

        if (req.getCursor() == null) {
            // ── First page ───────────────────────────────────────────────
            rows = conversationRepo.findFirstPage(
                    req.getProjectId(),
                    status,
                    assignedType,
                    req.getAssignedId(),
                    unreadOnly,
                    activeSession,
                    search,
                    limit
            );
        } else {
            // ── Subsequent pages ─────────────────────────────────────────
            long[]  parts      = CursorUtil.decode(req.getCursor());
            Instant cursorTime = Instant.ofEpochMilli(parts[0]);
            long    cursorId   = parts[1];

            rows = conversationRepo.findNextPage(
                    req.getProjectId(),
                    status,
                    assignedType,
                    req.getAssignedId(),
                    unreadOnly,
                    activeSession,
                    search,
                    cursorTime,
                    cursorId,
                    limit
            );
        }

        // ── Detect next page via probe row ────────────────────────────────
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        // ── Build cursor from last row ────────────────────────────────────
        String nextCursor = null;
        if (hasMore) {
            InboxProjection last = rows.get(rows.size() - 1);
            nextCursor = CursorUtil.encode(last.getLastMessageAt(), last.getConversationId());
        }

        // ── totalCount only on first page (avoid repeated COUNT on every scroll) ──
        // Returns Long (boxed) — null on page 2+ so @JsonInclude(NON_NULL) omits it
        Long totalCount = null;
        if (req.getCursor() == null) {
            totalCount = conversationRepo.countFiltered(
                    req.getProjectId(),
                    status,
                    assignedType,
                    req.getAssignedId(),
                    unreadOnly,
                    activeSession,
                    search
            );
        }

        List<InboxItemResponse> data = rows.stream()
                .map(InboxItemResponse::from)
                .toList();

        return CursorPageResponse.<InboxItemResponse>builder()
                .data(data)
                .pageSize(data.size())
                .totalCount(totalCount)   // null on page 2+ → omitted from JSON
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}