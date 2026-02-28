package com.apargo.services.message_report.repository;

import com.apargo.services.message_report.entity.Message;
import com.apargo.services.message_report.projection.MessageProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // ══════════════════════════════════════════════════════════════════════
    //  CONVERSATION DETAIL — keyset cursor (created_at DESC, id DESC)
    //  Newest messages first; frontend scrolls up to load older pages.
    //  Hits idx_conversation_time (conversation_id, created_at) directly.
    //
    //  Selected fields map exactly to columns in apargo_report.messages
    //  — no added or renamed columns required.
    // ══════════════════════════════════════════════════════════════════════

    /** FIRST PAGE — no cursor. */
    @Query("""
        SELECT
            m.id                AS messageId,
            m.uuid              AS uuid,
            m.direction         AS direction,
            m.messageType       AS messageType,
            m.status            AS status,
            m.bodyText          AS bodyText,
            m.templateName      AS templateName,
            m.templateLanguage  AS templateLanguage,
            m.templateVars      AS templateVars,
            m.mediaAssetId      AS mediaAssetId,
            m.providerMessageId AS providerMessageId,
            m.createdByType     AS createdByType,
            m.createdById       AS createdById,
            m.createdAt         AS createdAt,
            m.sentAt            AS sentAt,
            m.deliveredAt       AS deliveredAt,
            m.readAt            AS readAt,
            msr.isSent          AS isSent,
            msr.isDelivered     AS isDelivered,
            msr.isRead          AS isRead,
            msr.isFailed        AS isFailed
        FROM Message m
        LEFT JOIN MessageStatusRollup msr ON msr.messageId = m.id
        WHERE m.conversation.id = :conversationId
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<MessageProjection> findFirstPage(
            @Param("conversationId") Long conversationId,
            Pageable pageable
    );

    /** NEXT PAGES — keyset cursor. O(1) regardless of depth. */
    @Query("""
        SELECT
            m.id                AS messageId,
            m.uuid              AS uuid,
            m.direction         AS direction,
            m.messageType       AS messageType,
            m.status            AS status,
            m.bodyText          AS bodyText,
            m.templateName      AS templateName,
            m.templateLanguage  AS templateLanguage,
            m.templateVars      AS templateVars,
            m.mediaAssetId      AS mediaAssetId,
            m.providerMessageId AS providerMessageId,
            m.createdByType     AS createdByType,
            m.createdById       AS createdById,
            m.createdAt         AS createdAt,
            m.sentAt            AS sentAt,
            m.deliveredAt       AS deliveredAt,
            m.readAt            AS readAt,
            msr.isSent          AS isSent,
            msr.isDelivered     AS isDelivered,
            msr.isRead          AS isRead,
            msr.isFailed        AS isFailed
        FROM Message m
        LEFT JOIN MessageStatusRollup msr ON msr.messageId = m.id
        WHERE m.conversation.id = :conversationId
          AND (m.createdAt < :cursorTime
               OR (m.createdAt = :cursorTime AND m.id < :cursorId))
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<MessageProjection> findNextPage(
            @Param("conversationId") Long conversationId,
            @Param("cursorTime")     Instant cursorTime,
            @Param("cursorId")       Long cursorId,
            Pageable pageable
    );

    /** Total message count — used for first-page badge only. */
    @Query("SELECT COUNT(m.id) FROM Message m WHERE m.conversation.id = :conversationId")
    long countByConversationId(@Param("conversationId") Long conversationId);

    // ══════════════════════════════════════════════════════════════════════
    //  MARK AS READ — single UPDATE, no entity load
    // ══════════════════════════════════════════════════════════════════════

    @Modifying
    @Query("""
        UPDATE Conversation c
        SET c.unreadCount = 0,
            c.updatedAt   = CURRENT_TIMESTAMP
        WHERE c.id = :conversationId
          AND c.unreadCount > 0
        """)
    int markConversationAsRead(@Param("conversationId") Long conversationId);
}