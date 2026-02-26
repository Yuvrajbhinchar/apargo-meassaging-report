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
    private final long     totalCount;    // total matching records (for display "X conversations")
    private final String   nextCursor;    // null means no more pages
    private final boolean  hasMore;
}