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
    //  Shows newest messages first; frontend scrolls up to load older.
    //  Hits idx_conversation_time (conversation_id, created_at) directly.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * FIRST PAGE — no cursor, returns newest messages.
     */
    @Query("""
        SELECT
            m.id                  AS messageId,
            m.uuid                AS uuid,
            m.direction           AS direction,
            m.messageType         AS messageType,
            m.status              AS status,
            m.bodyText            AS bodyText,
            m.caption             AS caption,
            m.templateName        AS templateName,
            m.templateLanguage    AS templateLanguage,
            m.mediaAssetId        AS mediaAssetId,
            m.providerMessageId   AS providerMessageId,
            m.replyToProviderId   AS replyToProviderId,
            m.createdByType       AS createdByType,
            m.createdById         AS createdById,
            m.createdAt           AS createdAt,
            m.sentAt              AS sentAt,
            m.deliveredAt         AS deliveredAt,
            m.readAt              AS readAt,
            msr.isSent            AS isSent,
            msr.isDelivered       AS isDelivered,
            msr.isRead            AS isRead,
            msr.isFailed          AS isFailed
        FROM Message m
        LEFT JOIN MessageStatusRollup msr ON msr.messageId = m.id
        WHERE m.conversation.id = :conversationId
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<MessageProjection> findFirstPage(
            @Param("conversationId") Long conversationId,
            Pageable pageable
    );

    /**
     * NEXT PAGES — cursor carries (createdAt, messageId) from last row.
     * Keyset avoids OFFSET scan — O(1) depth-independent.
     */
    @Query("""
        SELECT
            m.id                  AS messageId,
            m.uuid                AS uuid,
            m.direction           AS direction,
            m.messageType         AS messageType,
            m.status              AS status,
            m.bodyText            AS bodyText,
            m.caption             AS caption,
            m.templateName        AS templateName,
            m.templateLanguage    AS templateLanguage,
            m.mediaAssetId        AS mediaAssetId,
            m.providerMessageId   AS providerMessageId,
            m.replyToProviderId   AS replyToProviderId,
            m.createdByType       AS createdByType,
            m.createdById         AS createdById,
            m.createdAt           AS createdAt,
            m.sentAt              AS sentAt,
            m.deliveredAt         AS deliveredAt,
            m.readAt              AS readAt,
            msr.isSent            AS isSent,
            msr.isDelivered       AS isDelivered,
            msr.isRead            AS isRead,
            msr.isFailed          AS isFailed
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

    /**
     * Count total messages in a conversation — for first-page total.
     */
    @Query("SELECT COUNT(m.id) FROM Message m WHERE m.conversation.id = :conversationId")
    long countByConversationId(@Param("conversationId") Long conversationId);

    // ══════════════════════════════════════════════════════════════════════
    //  MARK AS READ — reset unread counter on conversation
    //  Called when agent opens / views a conversation.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Zero out the unread counter for the conversation in a single UPDATE.
     * No entity load needed — avoids dirty-checking overhead.
     */
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