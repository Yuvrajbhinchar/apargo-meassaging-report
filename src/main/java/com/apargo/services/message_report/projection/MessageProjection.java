package com.apargo.services.message_report.projection;

import com.apargo.services.message_report.enums.CreatedByType;
import com.apargo.services.message_report.enums.MessageDirection;
import com.apargo.services.message_report.enums.MessageStatus;
import com.apargo.services.message_report.enums.MessageType;

import java.time.Instant;

/**
 * JPQL projection for the conversation message list.
 * Joins messages + message_status_rollup in one query — no N+1, no lazy issues.
 * Fields are strictly limited to columns that exist in apargo_report.messages.
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
    String           getTemplateName();
    String           getTemplateLanguage();
    /** templateVars JSON — used to substitute {{1}}, {{2}} placeholders in template bubbles */
    String           getTemplateVars();
    Long             getMediaAssetId();

    // ── Provider ──────────────────────────────────────────────────────────
    String           getProviderMessageId();

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