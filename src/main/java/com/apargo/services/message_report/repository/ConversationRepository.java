package com.apargo.services.message_report.repository;

import com.apargo.services.message_report.entity.Conversation;
import com.apargo.services.message_report.projection.InboxProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // ══════════════════════════════════════════════════════════════════════
    //  CORE INBOX QUERY
    //  Single JOIN query — 3 tables max, uses idx_inbox (project_id, waba_account_id, last_message_at)
    //  Cursor pagination: keyset on (last_message_at DESC, id DESC)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * FIRST PAGE — no cursor yet.
     * Used for both Inbox (filter by status) and Message History (all statuses).
     */
    @Query("""
        SELECT
            conv.id               AS conversationId,
            conv.contactId        AS contactId,
            conv.wabaAccountId    AS wabaAccountId,
            conv.status           AS status,
            conv.assignedType     AS assignedType,
            conv.assignedId       AS assignedId,
            conv.lastMessageAt    AS lastMessageAt,
            conv.lastMessageDirection AS lastMessageDirection,
            conv.lastMessagePreview   AS lastMessagePreview,
            conv.unreadCount      AS unreadCount,
            conv.conversationOpenUntil AS conversationOpenUntil,
            conv.lastInboundAt    AS lastInboundAt,
            conv.lastMessageId    AS lastMessageId,
            c.displayName         AS contactName,
            c.waPhoneE164         AS contactPhone,
            msr.isSent            AS isSent,
            msr.isDelivered       AS isDelivered,
            msr.isRead            AS isRead,
            msr.isFailed          AS isFailed
        FROM Conversation conv
        JOIN Contact c        ON c.id       = conv.contactId
        LEFT JOIN MessageStatusRollup msr ON msr.messageId = conv.lastMessageId
        WHERE conv.projectId = :projectId
          AND (:status       IS NULL OR conv.status       = :status)
          AND (:assignedType IS NULL OR conv.assignedType = :assignedType)
          AND (:assignedId   IS NULL OR conv.assignedId   = :assignedId)
          AND (:unreadOnly   = FALSE  OR conv.unreadCount > 0)
          AND (:activeSession = FALSE OR conv.conversationOpenUntil > CURRENT_TIMESTAMP)
          AND (:search       IS NULL
               OR LOWER(c.displayName)  LIKE LOWER(CONCAT('%', :search, '%'))
               OR c.waPhoneE164         LIKE CONCAT('%', :search, '%'))
        ORDER BY conv.lastMessageAt DESC, conv.id DESC
        """)
    List<InboxProjection> findFirstPage(
            @Param("projectId")    Long projectId,
            @Param("status")       Object status,
            @Param("assignedType") Object assignedType,
            @Param("assignedId")   Long assignedId,
            @Param("unreadOnly")   boolean unreadOnly,
            @Param("activeSession") boolean activeSession,
            @Param("search")       String search,
            Pageable pageable      // LIMIT only — no offset
    );

    /**
     * NEXT PAGES — cursor is (lastMessageAt, conversationId) from previous last row.
     * Uses the index idx_inbox directly via the keyset condition.
     */
    @Query("""
        SELECT
            conv.id               AS conversationId,
            conv.contactId        AS contactId,
            conv.wabaAccountId    AS wabaAccountId,
            conv.status           AS status,
            conv.assignedType     AS assignedType,
            conv.assignedId       AS assignedId,
            conv.lastMessageAt    AS lastMessageAt,
            conv.lastMessageDirection AS lastMessageDirection,
            conv.lastMessagePreview   AS lastMessagePreview,
            conv.unreadCount      AS unreadCount,
            conv.conversationOpenUntil AS conversationOpenUntil,
            conv.lastInboundAt    AS lastInboundAt,
            conv.lastMessageId    AS lastMessageId,
            c.displayName         AS contactName,
            c.waPhoneE164         AS contactPhone,
            msr.isSent            AS isSent,
            msr.isDelivered       AS isDelivered,
            msr.isRead            AS isRead,
            msr.isFailed          AS isFailed
        FROM Conversation conv
        JOIN Contact c        ON c.id       = conv.contactId
        LEFT JOIN MessageStatusRollup msr ON msr.messageId = conv.lastMessageId
        WHERE conv.projectId = :projectId
          AND (:status       IS NULL OR conv.status       = :status)
          AND (:assignedType IS NULL OR conv.assignedType = :assignedType)
          AND (:assignedId   IS NULL OR conv.assignedId   = :assignedId)
          AND (:unreadOnly   = FALSE  OR conv.unreadCount > 0)
          AND (:activeSession = FALSE OR conv.conversationOpenUntil > CURRENT_TIMESTAMP)
          AND (:search       IS NULL
               OR LOWER(c.displayName)  LIKE LOWER(CONCAT('%', :search, '%'))
               OR c.waPhoneE164         LIKE CONCAT('%', :search, '%'))
          AND (conv.lastMessageAt < :cursorTime
               OR (conv.lastMessageAt = :cursorTime AND conv.id < :cursorId))
        ORDER BY conv.lastMessageAt DESC, conv.id DESC
        """)
    List<InboxProjection> findNextPage(
            @Param("projectId")    Long projectId,
            @Param("status")       Object status,
            @Param("assignedType") Object assignedType,
            @Param("assignedId")   Long assignedId,
            @Param("unreadOnly")   boolean unreadOnly,
            @Param("activeSession") boolean activeSession,
            @Param("search")       String search,
            @Param("cursorTime")   Instant cursorTime,
            @Param("cursorId")     Long cursorId,
            Pageable pageable      // LIMIT only
    );

    // ══════════════════════════════════════════════════════════════════════
    //  COUNT QUERIES (for totalCount in response header)
    //  Run async or cache — do NOT run on every page request
    // ══════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT COUNT(conv.id)
        FROM Conversation conv
        JOIN Contact c ON c.id = conv.contactId
        WHERE conv.projectId = :projectId
          AND (:status       IS NULL OR conv.status       = :status)
          AND (:assignedType IS NULL OR conv.assignedType = :assignedType)
          AND (:assignedId   IS NULL OR conv.assignedId   = :assignedId)
          AND (:unreadOnly   = FALSE  OR conv.unreadCount > 0)
          AND (:activeSession = FALSE OR conv.conversationOpenUntil > CURRENT_TIMESTAMP)
          AND (:search       IS NULL
               OR LOWER(c.displayName)  LIKE LOWER(CONCAT('%', :search, '%'))
               OR c.waPhoneE164         LIKE CONCAT('%', :search, '%'))
        """)
    long countFiltered(
            @Param("projectId")    Long projectId,
            @Param("status")       Object status,
            @Param("assignedType") Object assignedType,
            @Param("assignedId")   Long assignedId,
            @Param("unreadOnly")   boolean unreadOnly,
            @Param("activeSession") boolean activeSession,
            @Param("search")       String search
    );

    // ── Fast unread count for badge (no joins needed) ────────────────────
    @Query("SELECT COALESCE(SUM(conv.unreadCount), 0) FROM Conversation conv WHERE conv.projectId = :projectId AND conv.status = com.apargo.services.message_report.enums.ConversationStatus.OPEN")
    long sumUnreadByProject(@Param("projectId") Long projectId);
}