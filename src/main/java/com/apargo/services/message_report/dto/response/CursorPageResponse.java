package com.apargo.services.message_report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CursorPageResponse<T> {

    private final List<T>  data;
    private final int      pageSize;

    /**
     * Total matching conversations — ONLY on first page (cursor == null).
     * Long (boxed) instead of long (primitive) so @JsonInclude(NON_NULL) can omit
     * it on page 2+. A primitive long defaults to 0, which is a valid value and
     * would be serialized on every page, confusing the frontend.
     *
     * Frontend pattern:
     *   if (response.totalCount != null) updateBadge(response.totalCount)
     */
    private final Long     totalCount;

    private final String   nextCursor;   // null = no more pages
    private final boolean  hasMore;
}