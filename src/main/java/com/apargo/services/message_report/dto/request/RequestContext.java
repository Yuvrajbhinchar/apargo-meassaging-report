package com.apargo.services.message_report.dto.request;

import lombok.Builder;
import lombok.Getter;

/**
 * Holds caller identity extracted from request headers.
 *
 * Headers:
 *   X-Organization-Id  → organizationId (required)
 *   X-User-Id          → userId (optional — if present, scopes inbox to this user)
 */
@Getter
@Builder
public class RequestContext {
    private final Long organizationId;
    private final Long userId;
}