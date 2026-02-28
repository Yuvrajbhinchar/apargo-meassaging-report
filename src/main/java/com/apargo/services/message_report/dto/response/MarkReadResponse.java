package com.apargo.services.message_report.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarkReadResponse {
    private final Long conversationId;
    private final boolean updated;        // false if already at 0 (idempotent)
    private final String message;
}