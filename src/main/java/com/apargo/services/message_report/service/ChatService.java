package com.apargo.services.message_report.service;

import com.apargo.services.message_report.dto.response.*;
import com.apargo.services.message_report.entity.Contact;
import com.apargo.services.message_report.entity.Conversation;
import com.apargo.services.message_report.entity.WhatsappTemplate;
import com.apargo.services.message_report.enums.MessageType;
import com.apargo.services.message_report.projection.MessageProjection;
import com.apargo.services.message_report.repository.ConversationRepository;
import com.apargo.services.message_report.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_PAGE_SIZE = 50;

    private final MessageRepository      messageRepo;
    private final ConversationRepository conversationRepo;
    private final TemplateLoaderService  templateLoaderService;  // ← replaces direct templateRepo
    private final EntityManager          em;

    // ══════════════════════════════════════════════════════════════════════
    //  GET CONVERSATION MESSAGES
    //  Cursor-based, newest-first.
    //  Frontend loads page 1 (newest), scrolls up to load older pages.
    //
    //  Performance strategy:
    //   1. One JPQL query for messages + rollup (no N+1).
    //   2. Collect unique templateName values from TEMPLATE-type messages.
    //   3. One batch query to apargo_wa_template schema for all templates
    //      (executed in an independent REQUIRES_NEW transaction so any
    //      failure does NOT poison the outer read transaction).
    //   4. Enrich message DTOs with template details in-memory.
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ConversationDetailResponse getMessages(
            Long   conversationId,
            String cursor,
            int    size
    ) {
        // ── 1. Load conversation (also validates it exists) ───────────────
        Conversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        // ── 2. Resolve contact name + phone ───────────────────────────────
        String contactName  = null;
        String contactPhone = null;
        try {
            Contact contact = em.find(Contact.class, conv.getContactId());
            if (contact != null) {
                contactName  = contact.getDisplayName();
                contactPhone = contact.getWaPhoneE164();
            }
        } catch (Exception e) {
            log.warn("Could not resolve contact {} for conversation {}: {}",
                    conv.getContactId(), conversationId, e.getMessage());
        }

        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);

        // ── 3. Fetch messages (size+1 probe for hasMore) ──────────────────
        List<MessageProjection> rows = fetchPage(conversationId, cursor, effectiveSize + 1);

        boolean hasMore = rows.size() > effectiveSize;
        if (hasMore) rows = rows.subList(0, effectiveSize);

        // ── 4. Build cursor from LAST row ──────────────────────────────────
        String nextCursor = null;
        if (hasMore) {
            MessageProjection last = rows.get(rows.size() - 1);
            nextCursor = CursorUtil.encode(last.getCreatedAt(), last.getMessageId());
        }

        // ── 5. totalCount only on first page ──────────────────────────────
        Long totalCount = null;
        if (cursor == null) {
            totalCount = messageRepo.countByConversationId(conversationId);
        }

        // ── 6. Map projections → base DTOs ────────────────────────────────
        List<ChatMessageResponse> data = rows.stream()
                .map(ChatMessageResponse::from)
                .collect(Collectors.toList());

        // ── 7. Enrich TEMPLATE-type messages with template details ─────────
        //      Uses TemplateLoaderService (REQUIRES_NEW) so a template-fetch
        //      failure never rolls back the outer read transaction.
        data = enrichWithTemplateDetails(data, rows, conv.getProjectId());

        // ── 8. Build messages page ────────────────────────────────────────
        CursorPageResponse<ChatMessageResponse> messagePage = CursorPageResponse
                .<ChatMessageResponse>builder()
                .data(data)
                .pageSize(data.size())
                .totalCount(totalCount)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();

        // ── 9. Session window ─────────────────────────────────────────────
        Instant now   = Instant.now();
        Instant until = conv.getConversationOpenUntil();
        boolean active = until != null && until.isAfter(now);

        return ConversationDetailResponse.builder()
                .conversationId(conv.getId())
                .contactId(conv.getContactId())
                .contactName(contactName)
                .contactPhone(contactPhone)
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
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public MarkReadResponse markAsRead(Long conversationId) {
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

        long[]  parts      = CursorUtil.decode(cursor);
        Instant cursorTime = Instant.ofEpochMilli(parts[0]);
        long    cursorId   = parts[1];

        return messageRepo.findNextPage(conversationId, cursorTime, cursorId, pageable);
    }

    /**
     * Batch-enrich TEMPLATE-type message DTOs with their full template structure.
     *
     * Strategy:
     *  1. Collect distinct templateName values from TEMPLATE rows.
     *  2. Delegate to TemplateLoaderService (runs in REQUIRES_NEW transaction)
     *     so any failure there does NOT mark the outer transaction as rollback-only.
     *  3. Build a lookup map (name|language → template entity).
     *  4. Attach TemplateDetailResponse to each TEMPLATE message DTO.
     *
     * Non-template messages are returned untouched.
     * If template loading fails, messages are returned without templateDetail (graceful degradation).
     */
    private List<ChatMessageResponse> enrichWithTemplateDetails(
            List<ChatMessageResponse> dtos,
            List<MessageProjection>   projections,
            Long                      projectId
    ) {
        // Collect names of templates referenced on this page
        Set<String> templateNames = projections.stream()
                .filter(p -> MessageType.TEMPLATE.equals(p.getMessageType()))
                .filter(p -> p.getTemplateName() != null)
                .map(MessageProjection::getTemplateName)
                .collect(Collectors.toSet());

        if (templateNames.isEmpty()) {
            return dtos; // no templates on this page — nothing to do
        }

        // Delegate to isolated REQUIRES_NEW transaction — failure won't poison outer tx
        Map<String, WhatsappTemplate> templateMap = templateLoaderService.loadBatch(
                projectId, List.copyOf(templateNames)
        );

        if (templateMap.isEmpty()) {
            // Template load failed or returned nothing — return messages without detail
            return dtos;
        }

        // Build a fast projection lookup by messageId so we can get templateVars
        Map<Long, MessageProjection> projByMessageId = projections.stream()
                .collect(Collectors.toMap(
                        MessageProjection::getMessageId,
                        Function.identity()
                ));

        // Enrich each TEMPLATE message with its detail
        return dtos.stream()
                .map(dto -> {
                    if (!MessageType.TEMPLATE.equals(dto.getMessageType())) return dto;
                    if (dto.getTemplateName() == null) return dto;

                    String key = dto.getTemplateName() + "|" + dto.getTemplateLanguage();
                    WhatsappTemplate tmpl = templateMap.get(key);

                    if (tmpl == null) {
                        // Template may have been deleted or not yet approved — skip silently
                        log.debug("Template not found for key '{}' in project {}", key, projectId);
                        return dto;
                    }

                    // Get templateVars JSON from the original projection for variable substitution
                    MessageProjection proj = projByMessageId.get(dto.getMessageId());
                    String templateVars = proj != null ? proj.getTemplateVars() : null;

                    TemplateDetailResponse detail = TemplateDetailResponse.from(tmpl, templateVars);
                    return dto.withTemplateDetail(detail);
                })
                .collect(Collectors.toList());
    }
}