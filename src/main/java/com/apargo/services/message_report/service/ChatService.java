package com.apargo.services.message_report.service;

import com.apargo.services.message_report.dto.response.*;
import com.apargo.services.message_report.entity.Conversation;
import com.apargo.services.message_report.projection.MessageProjection;
import com.apargo.services.message_report.repository.ConversationRepository;
import com.apargo.services.message_report.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_PAGE_SIZE = 50;

    private final MessageRepository      messageRepo;
    private final ConversationRepository conversationRepo;

    // ══════════════════════════════════════════════════════════════════════
    //  GET CONVERSATION MESSAGES
    //  Cursor-based, newest-first.
    //  Frontend loads page 1 (newest), scrolls up → requests older pages.
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ConversationDetailResponse getMessages(
            Long conversationId,
            String cursor,
            int size
    ) {
        // ── Load conversation (validates it exists) ────────────────────────
        Conversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);

        // Fetch size+1 to probe for next page — avoids extra COUNT query
        List<MessageProjection> rows = fetchPage(conversationId, cursor, effectiveSize + 1);

        boolean hasMore = rows.size() > effectiveSize;
        if (hasMore) rows = rows.subList(0, effectiveSize);

        // ── Build next cursor from LAST row returned ───────────────────────
        String nextCursor = null;
        if (hasMore) {
            MessageProjection last = rows.get(rows.size() - 1);
            nextCursor = CursorUtil.encode(last.getCreatedAt(), last.getMessageId());
        }

        // ── Total count only on first page ────────────────────────────────
        Long totalCount = null;
        if (cursor == null) {
            totalCount = messageRepo.countByConversationId(conversationId);
        }

        List<ChatMessageResponse> data = rows.stream()
                .map(ChatMessageResponse::from)
                .toList();

        CursorPageResponse<ChatMessageResponse> messagePage = CursorPageResponse
                .<ChatMessageResponse>builder()
                .data(data)
                .pageSize(data.size())
                .totalCount(totalCount)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();

        // ── Session window ────────────────────────────────────────────────
        Instant now   = Instant.now();
        Instant until = conv.getConversationOpenUntil();
        boolean active = until != null && until.isAfter(now);

        return ConversationDetailResponse.builder()
                .conversationId(conv.getId())
                .contactId(conv.getContactId())
                .status(conv.getStatus())
                .assignedType(conv.getAssignedType())
                .assignedId(conv.getAssignedId())
                .conversationOpenUntil(until)
                .isSessionActive(active)
                .sessionRemainingMs(active ? until.toEpochMilli() - now.toEpochMilli() : null)
                .messages(messagePage)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MARK CONVERSATION AS READ
    //  Single UPDATE — no entity load, no dirty-check overhead.
    //  Idempotent: safe to call even if already 0.
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public MarkReadResponse markAsRead(Long conversationId) {
        // Validate conversation exists before issuing UPDATE
        if (!conversationRepo.existsById(conversationId)) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }

        int rowsAffected = messageRepo.markConversationAsRead(conversationId);
        boolean updated  = rowsAffected > 0;

        log.debug("markAsRead conversationId={} updated={}", conversationId, updated);

        return MarkReadResponse.builder()
                .conversationId(conversationId)
                .updated(updated)
                .message(updated ? "Conversation marked as read." : "Already up to date.")
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private List<MessageProjection> fetchPage(Long conversationId, String cursor, int limit) {
        PageRequest pageable = PageRequest.of(0, limit);

        if (cursor == null) {
            return messageRepo.findFirstPage(conversationId, pageable);
        }

        long[] parts      = CursorUtil.decode(cursor);
        Instant cursorTime = Instant.ofEpochMilli(parts[0]);
        long    cursorId   = parts[1];

        return messageRepo.findNextPage(conversationId, cursorTime, cursorId, pageable);
    }
}