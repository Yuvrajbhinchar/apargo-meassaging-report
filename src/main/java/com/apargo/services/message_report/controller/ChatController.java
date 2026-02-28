package com.apargo.services.message_report.controller;

import com.apargo.services.message_report.dto.response.ConversationDetailResponse;
import com.apargo.services.message_report.dto.response.MarkReadResponse;
import com.apargo.services.message_report.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Chat Conversation Controller
 *
 * ┌────────────────────────────────────────────────────────────────────────┐
 * │  GET  /api/chats/conversation/{id}/messages                           │
 * │       Load one-to-one conversation messages (cursor-paginated).       │
 * │       For TEMPLATE messages the full template structure is embedded.  │
 * │                                                                        │
 * │  POST /api/chats/conversation/{id}/mark-read                          │
 * │       Zero-out the unread counter. Idempotent (safe to call twice).   │
 * └────────────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/chats/conversation")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ══════════════════════════════════════════════════════════════════════
    //  GET CONVERSATION MESSAGES
    //
    //  Query params:
    //   cursor   – omit for first page; pass nextCursor from previous response
    //   size     – page size (default 20, max 50)
    //
    //  Response (ConversationDetailResponse):
    //   {
    //     "conversationId": 42,
    //     "contactName": "Raj Kumar",
    //     "contactPhone": "+919876543210",
    //     "status": "OPEN",
    //     "isSessionActive": true,
    //     "sessionRemainingMs": 43200000,
    //     "messages": {
    //       "data": [
    //         {
    //           "messageId": 101,
    //           "messageType": "TEMPLATE",
    //           "templateDetail": {
    //             "name": "order_confirmed",
    //             "components": [
    //               { "componentType": "HEADER", "format": "IMAGE", "mediaUrl": "..." },
    //               { "componentType": "BODY",   "renderedText": "Hi Raj, your order #1234 ..." },
    //               { "componentType": "BUTTONS","buttons": [...] }
    //             ]
    //           },
    //           "ticks": { "isSent": true, "isDelivered": true, "isRead": false }
    //         },
    //         { "messageId": 100, "messageType": "TEXT", "bodyText": "Hello!" }
    //       ],
    //       "hasMore": true,
    //       "nextCursor": "MTY5..."
    //     }
    //   }
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ConversationDetailResponse> getMessages(
            @PathVariable                      Long   conversationId,
            @RequestParam(required = false)    String cursor,
            @RequestParam(defaultValue = "20") int    size
    ) {
        return ResponseEntity.ok(chatService.getMessages(conversationId, cursor, size));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MARK CONVERSATION AS READ
    //
    //  Resets the unreadCount on the conversation to 0 in a single UPDATE.
    //  Call this when the agent opens / views a conversation.
    //  Idempotent — safe to call even if already read.
    //
    //  Response:
    //   { "conversationId": 42, "updated": true, "message": "Conversation marked as read." }
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/{conversationId}/mark-read")
    public ResponseEntity<MarkReadResponse> markAsRead(
            @PathVariable Long conversationId
    ) {
        return ResponseEntity.ok(chatService.markAsRead(conversationId));
    }
}