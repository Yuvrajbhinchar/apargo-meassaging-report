package com.apargo.services.message_report.dto.response;

import com.apargo.services.message_report.enums.CreatedByType;
import com.apargo.services.message_report.enums.MessageDirection;
import com.apargo.services.message_report.enums.MessageStatus;
import com.apargo.services.message_report.enums.MessageType;
import com.apargo.services.message_report.projection.MessageProjection;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageResponse {

    // ── Identity ──────────────────────────────────────────────────────────
    private final Long             messageId;
    private final String           uuid;

    // ── Direction & type ──────────────────────────────────────────────────
    private final MessageDirection direction;      // INBOUND | OUTBOUND
    private final MessageType      messageType;    // TEXT, IMAGE, TEMPLATE …
    private final MessageStatus    status;

    // ── Content ───────────────────────────────────────────────────────────
    private final String           bodyText;
    private final String           caption;
    private final String           templateName;
    private final String           templateLanguage;
    private final Long             mediaAssetId;  // resolve URL in frontend if needed

    // ── Threading ─────────────────────────────────────────────────────────
    private final String           providerMessageId;
    private final String           replyToProviderId;   // null if not a reply

    // ── Authorship ────────────────────────────────────────────────────────
    private final CreatedByType    createdByType;
    private final Long             createdById;

    // ── Timestamps ────────────────────────────────────────────────────────
    private final Instant          createdAt;
    private final Instant          sentAt;
    private final Instant          deliveredAt;
    private final Instant          readAt;

    // ── Delivery ticks ────────────────────────────────────────────────────
    private final TickStatus       ticks;

    // ── Factory ───────────────────────────────────────────────────────────

    public static ChatMessageResponse from(MessageProjection p) {
        return ChatMessageResponse.builder()
                .messageId(p.getMessageId())
                .uuid(p.getUuid())
                .direction(p.getDirection())
                .messageType(p.getMessageType())
                .status(p.getStatus())
                .bodyText(p.getBodyText())
                .caption(p.getCaption())
                .templateName(p.getTemplateName())
                .templateLanguage(p.getTemplateLanguage())
                .mediaAssetId(p.getMediaAssetId())
                .providerMessageId(p.getProviderMessageId())
                .replyToProviderId(p.getReplyToProviderId())
                .createdByType(p.getCreatedByType())
                .createdById(p.getCreatedById())
                .createdAt(p.getCreatedAt())
                .sentAt(p.getSentAt())
                .deliveredAt(p.getDeliveredAt())
                .readAt(p.getReadAt())
                .ticks(TickStatus.builder()
                        .isSent(Boolean.TRUE.equals(p.getIsSent()))
                        .isDelivered(Boolean.TRUE.equals(p.getIsDelivered()))
                        .isRead(Boolean.TRUE.equals(p.getIsRead()))
                        .isFailed(Boolean.TRUE.equals(p.getIsFailed()))
                        .build())
                .build();
    }
}