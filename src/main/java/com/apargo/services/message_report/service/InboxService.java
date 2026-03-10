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

    public CursorPageResponse<InboxItemResponse> getInbox(InboxFilterRequest req) {
        req.setStatus(ConversationStatus.OPEN);
        return fetchPage(req, true);
    }

    public CursorPageResponse<InboxItemResponse> getInboxDataOnly(InboxFilterRequest req) {
        req.setStatus(ConversationStatus.OPEN);
        return fetchPage(req, false);
    }

    public ConversationCountResponse getInboxCount(InboxFilterRequest req) {
        req.setStatus(ConversationStatus.OPEN);

        long total = conversationRepo.countFiltered(
                req.getProjectId(),
                req.getOrganizationId(),
                req.getStatus(),
                req.getUserId(),
                req.getAssignedType(),
                req.getAssignedId(),
                bool(req.getUnreadOnly()),
                bool(req.getActiveSession()),
                req.getFromDate(),
                req.getToDate(),
                blankToNull(req.getSearch())
        );

        long unreadTotal = conversationRepo.sumUnreadByProject(
                req.getProjectId(),
                req.getOrganizationId()
        );

        return ConversationCountResponse.builder()
                .totalCount(total)
                .unreadTotal(unreadTotal)
                .build();
    }

    public CursorPageResponse<InboxItemResponse> getMessageHistory(InboxFilterRequest req) {
        return fetchPage(req, true);
    }

    public CursorPageResponse<InboxItemResponse> getMessageHistoryDataOnly(InboxFilterRequest req) {
        return fetchPage(req, false);
    }

    public ConversationCountResponse getMessageHistoryCount(InboxFilterRequest req) {
        long total = conversationRepo.countFiltered(
                req.getProjectId(),
                req.getOrganizationId(),
                req.getStatus(),
                req.getUserId(),
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
                .unreadTotal(null)
                .build();
    }

    private CursorPageResponse<InboxItemResponse> fetchPage(InboxFilterRequest req, boolean withCount) {
        int      size          = Math.min(req.getSize(), MAX_PAGE_SIZE);
        Pageable limit         = PageRequest.of(0, size + 1);
        boolean  unreadOnly    = bool(req.getUnreadOnly());
        boolean  activeSession = bool(req.getActiveSession());
        String   search        = blankToNull(req.getSearch());

        List<InboxProjection> rows;

        if (req.getCursor() == null) {
            rows = conversationRepo.findFirstPage(
                    req.getProjectId(),
                    req.getOrganizationId(),
                    req.getStatus(),
                    req.getUserId(),
                    req.getAssignedType(),
                    req.getAssignedId(),
                    unreadOnly, activeSession,
                    req.getFromDate(), req.getToDate(),
                    search,
                    limit
            );
        } else {
            long[]  parts      = CursorUtil.decode(req.getCursor());
            Instant cursorTime = Instant.ofEpochMilli(parts[0]);
            long    cursorId   = parts[1];

            rows = conversationRepo.findNextPage(
                    req.getProjectId(),
                    req.getOrganizationId(),
                    req.getStatus(),
                    req.getUserId(),
                    req.getAssignedType(),
                    req.getAssignedId(),
                    unreadOnly, activeSession,
                    req.getFromDate(), req.getToDate(),
                    search,
                    cursorTime, cursorId,
                    limit
            );
        }

        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        String nextCursor = null;
        if (hasMore) {
            InboxProjection last = rows.get(rows.size() - 1);
            nextCursor = CursorUtil.encode(last.getLastMessageAt(), last.getConversationId());
        }

        Long totalCount = null;
        if (withCount && req.getCursor() == null) {
            totalCount = conversationRepo.countFiltered(
                    req.getProjectId(),
                    req.getOrganizationId(),
                    req.getStatus(),
                    req.getUserId(),
                    req.getAssignedType(),
                    req.getAssignedId(),
                    unreadOnly, activeSession,
                    req.getFromDate(), req.getToDate(),
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

    private boolean bool(Boolean b)      { return Boolean.TRUE.equals(b); }
    private String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}