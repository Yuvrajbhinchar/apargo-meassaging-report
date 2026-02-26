package com.apargo.services.message_report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TickStatus {
    private final boolean isSent;
    private final boolean isDelivered;
    private final boolean isRead;
    private final boolean isFailed;
}