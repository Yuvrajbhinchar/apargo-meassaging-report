package com.apargo.services.message_report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Response for count-only endpoints:
 *   GET /api/chats/inbox/count
 *   GET /api/v1/get-messages-history/count
 *
 * Fields:
 *   totalCount   - total conversations matching the filters
 *   unreadTotal  - sum of unread_count across matching conversations
 *                  (only populated for inbox/OPEN queries, null for history)
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationCountResponse {

    /** Total conversations matching the current filter (status, assigned, search, etc.) */
    private final Long totalCount;

    /**
     * Total unread messages across all matching OPEN conversations.
     * Null for message history queries (non-OPEN status).
     * Use this to show the unread badge in the inbox header.
     */
    private final Long unreadTotal;
}