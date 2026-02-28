package com.apargo.services.message_report.projection;

import com.apargo.services.message_report.enums.CreatedByType;
import com.apargo.services.message_report.enums.MessageDirection;
import com.apargo.services.message_report.enums.MessageStatus;
import com.apargo.services.message_report.enums.MessageType;

import java.time.Instant;

/**
 * JPQL projection for the conversation message list.
 * Joins messages + message_status_rollup in one query — no N+1, no lazy issues.
 */
public interface MessageProjection {

    // ── Message identity ──────────────────────────────────────────────────
    Long             getMessageId();
    String           getUuid();
    MessageDirection getDirection();
    MessageType      getMessageType();
    MessageStatus    getStatus();

    // ── Content ───────────────────────────────────────────────────────────
    String           getBodyText();
    String           getCaption();
    String           getTemplateName();
    String           getTemplateLanguage();
    Long             getMediaAssetId();

    // ── Provider ──────────────────────────────────────────────────────────
    String           getProviderMessageId();
    String           getReplyToProviderId();

    // ── Authorship ────────────────────────────────────────────────────────
    CreatedByType    getCreatedByType();
    Long             getCreatedById();

    // ── Timestamps ────────────────────────────────────────────────────────
    Instant          getCreatedAt();
    Instant          getSentAt();
    Instant          getDeliveredAt();
    Instant          getReadAt();

    // ── Delivery ticks (from message_status_rollup) ───────────────────────
    Boolean          getIsSent();
    Boolean          getIsDelivered();
    Boolean          getIsRead();
    Boolean          getIsFailed();
}