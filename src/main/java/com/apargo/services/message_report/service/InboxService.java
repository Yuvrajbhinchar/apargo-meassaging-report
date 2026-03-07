package com.apargo.services.message_report.service;

import com.apargo.services.message_report.dto.request.InboxFilterRequest;
import com.apargo.services.message_report.dto.response.ConversationCountResponse;
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
    //  ① INBOX — data + totalCount  (original)
    //     GET /api/chats/inbox
    // ══════════════════════════════════════════════════════════════════════

    public CursorPageResponse<InboxItemResponse> getInbox(InboxFilterRequest req) {
        req.setStatus(ConversationStatus.OPEN);
        return fetchPage(req, true);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ② INBOX — data ONLY, no COUNT  (new faster endpoint)
    //     GET /api/chats/inbox/data
    // ══════════════════════════════════════════════════════════════════════

    public CursorPageResponse<InboxItemResponse> getInboxDataOnly(InboxFilterRequest req) {
        req.setStatus(ConversationStatus.OPEN);
        return fetchPage(req, false);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ③ INBOX — count ONLY  (new dedicated count endpoint)
    //     GET /api/chats/inbox/count
    //     Returns totalCount + unreadTotal
    // ══════════════════════════════════════════════════════════════════════

    public ConversationCountResponse getInboxCount(InboxFilterRequest req) {
        req.setStatus(ConversationStatus.OPEN);

        long total = conversationRepo.countFiltered(
                req.getProjectId(),
                req.getStatus(),
                req.getAssignedType(),
                req.getAssignedId(),
                bool(req.getUnreadOnly()),
                bool(req.getActiveSession()),
                req.getFromDate(),
                req.getToDate(),
                blankToNull(req.getSearch())
        );

        long unreadTotal = conversationRepo.sumUnreadByProject(req.getProjectId());

        return ConversationCountResponse.builder()
                .totalCount(total)
                .unreadTotal(unreadTotal)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ④ MESSAGE HISTORY — data + totalCount  (original)
    //     GET /api/v1/get-messages-history
    // ══════════════════════════════════════════════════════════════════════

    public CursorPageResponse<InboxItemResponse> getMessageHistory(InboxFilterRequest req) {
        return fetchPage(req, true);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ⑤ MESSAGE HISTORY — data ONLY, no COUNT  (new faster endpoint)
    //     GET /api/v1/get-messages-history/data
    // ══════════════════════════════════════════════════════════════════════

    public CursorPageResponse<InboxItemResponse> getMessageHistoryDataOnly(InboxFilterRequest req) {
        return fetchPage(req, false);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ⑥ MESSAGE HISTORY — count ONLY  (new dedicated count endpoint)
    //     GET /api/v1/get-messages-history/count
    //     Returns totalCount only (unreadTotal = null for history)
    // ══════════════════════════════════════════════════════════════════════

    public ConversationCountResponse getMessageHistoryCount(InboxFilterRequest req) {
        long total = conversationRepo.countFiltered(
                req.getProjectId(),
                req.getStatus(),
                req.getAssignedType(),
                req.getAssignedId(),
                bool(req.getUnreadOnly()),
                bool(req.getActiveSession()),
                req.getFromDate(),
                req.getToDate(),
                blankToNull(req.getSearch())
        );

        return ConversationCountResponse.builder()
                .totalCount(total)
                .unreadTotal(null)   // not relevant for history — omitted from JSON
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHARED CORE
    //  withCount = true  → runs COUNT on first page  (original behaviour)
    //  withCount = false → skips COUNT entirely      (data-only endpoints)
    // ══════════════════════════════════════════════════════════════════════

    private CursorPageResponse<InboxItemResponse> fetchPage(
            InboxFilterRequest req,
            boolean            withCount
    ) {
        int     size          = Math.min(req.getSize(), MAX_PAGE_SIZE);
        Pageable limit        = PageRequest.of(0, size + 1);   // +1 probe for hasMore
        boolean unreadOnly    = bool(req.getUnreadOnly());
        boolean activeSession = bool(req.getActiveSession());

        ConversationStatus status       = req.getStatus();
        AssignedType       assignedType = req.getAssignedType();
        String             search       = blankToNull(req.getSearch());
        Instant            fromDate     = req.getFromDate();
        Instant            toDate       = req.getToDate();

        List<InboxProjection> rows;

        if (req.getCursor() == null) {
            // ── First page ────────────────────────────────────────────────
            rows = conversationRepo.findFirstPage(
                    req.getProjectId(),
                    status, assignedType, req.getAssignedId(),
                    unreadOnly, activeSession,
                    fromDate, toDate,
                    search,
                    limit
            );
        } else {
            // ── Subsequent pages (cursor scroll) ──────────────────────────
            long[]  parts      = CursorUtil.decode(req.getCursor());
            Instant cursorTime = Instant.ofEpochMilli(parts[0]);
            long    cursorId   = parts[1];

            rows = conversationRepo.findNextPage(
                    req.getProjectId(),
                    status, assignedType, req.getAssignedId(),
                    unreadOnly, activeSession,
                    fromDate, toDate,
                    search,
                    cursorTime, cursorId,
                    limit
            );
        }

        // ── hasMore probe ──────────────────────────────────────────────────
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        // ── Build next cursor from last row ────────────────────────────────
        String nextCursor = null;
        if (hasMore) {
            InboxProjection last = rows.get(rows.size() - 1);
            nextCursor = CursorUtil.encode(last.getLastMessageAt(), last.getConversationId());
        }

        // ── totalCount — first page only, only when withCount = true ───────
        Long totalCount = null;
        if (withCount && req.getCursor() == null) {
            totalCount = conversationRepo.countFiltered(
                    req.getProjectId(),
                    status, assignedType, req.getAssignedId(),
                    unreadOnly, activeSession,
                    fromDate, toDate,
                    search
            );
        }

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

    private boolean bool(Boolean b)      { return Boolean.TRUE.equals(b); }
    private String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}