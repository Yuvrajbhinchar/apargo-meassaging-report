package com.apargo.services.message_report.service;

import com.apargo.services.message_report.dto.request.InboxFilterRequest;
import com.apargo.services.message_report.dto.response.CursorPageResponse;
import com.apargo.services.message_report.dto.response.CursorUtil;
import com.apargo.services.message_report.dto.response.InboxItemResponse;
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
    //  INBOX  — open conversations only (status = OPEN)
    // ══════════════════════════════════════════════════════════════════════

    public CursorPageResponse<InboxItemResponse> getInbox(InboxFilterRequest req) {
        // Force status = OPEN for inbox
        req.setStatus(ConversationStatus.OPEN);
        return fetchPage(req);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGE HISTORY — all conversations, no status filter by default
    // ══════════════════════════════════════════════════════════════════════

    public CursorPageResponse<InboxItemResponse> getMessageHistory(InboxFilterRequest req) {
        // status stays null unless caller explicitly filters (closed, archived, etc.)
        return fetchPage(req);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHARED CORE — both APIs use the same query, differ only in filters
    // ══════════════════════════════════════════════════════════════════════

    private CursorPageResponse<InboxItemResponse> fetchPage(InboxFilterRequest req) {

        int size = Math.min(req.getSize(), MAX_PAGE_SIZE);

        // We fetch size+1 rows — if we get more than size back, there's a next page
        Pageable limit = PageRequest.of(0, size + 1);

        boolean unreadOnly    = Boolean.TRUE.equals(req.getUnreadOnly());
        boolean activeSession = Boolean.TRUE.equals(req.getActiveSession());

        // Null-safe filter params (JPQL treats null as "no filter")
        Object statusParam       = req.getStatus();       // enum or null
        Object assignedTypeParam = req.getAssignedType(); // enum or null
        String searchParam       = blankToNull(req.getSearch());

        List<InboxProjection> rows;

        if (req.getCursor() == null) {
            // ── First page ──────────────────────────────────────────────
            rows = conversationRepo.findFirstPage(
                    req.getProjectId(),
                    statusParam,
                    assignedTypeParam,
                    req.getAssignedId(),
                    unreadOnly,
                    activeSession,
                    searchParam,
                    limit
            );
        } else {
            // ── Subsequent pages ─────────────────────────────────────────
            long[] parts     = CursorUtil.decode(req.getCursor());
            Instant cursorTime = Instant.ofEpochMilli(parts[0]);
            long    cursorId   = parts[1];

            rows = conversationRepo.findNextPage(
                    req.getProjectId(),
                    statusParam,
                    assignedTypeParam,
                    req.getAssignedId(),
                    unreadOnly,
                    activeSession,
                    searchParam,
                    cursorTime,
                    cursorId,
                    limit
            );
        }

        // ── Determine if there are more pages ─────────────────────────────
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size); // trim the extra probe row

        // ── Build next cursor from last row ───────────────────────────────
        String nextCursor = null;
        if (hasMore) {
            InboxProjection last = rows.get(rows.size() - 1);
            nextCursor = CursorUtil.encode(last.getLastMessageAt(), last.getConversationId());
        }

        // ── Get total count (only on first page to avoid repeated COUNT queries) ──
        long totalCount = 0;
        if (req.getCursor() == null) {
            totalCount = conversationRepo.countFiltered(
                    req.getProjectId(),
                    statusParam,
                    assignedTypeParam,
                    req.getAssignedId(),
                    unreadOnly,
                    activeSession,
                    searchParam
            );
        }

        // ── Map projections → response DTOs ──────────────────────────────
        List<InboxItemResponse> data = rows.stream()
                .map(InboxItemResponse::from)
                .toList();

        return CursorPageResponse.<InboxItemResponse>builder()
                .data(data)
                .pageSize(data.size())
                .totalCount(totalCount)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}