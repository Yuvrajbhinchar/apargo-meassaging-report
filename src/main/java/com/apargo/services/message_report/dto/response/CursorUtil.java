package com.apargo.services.message_report.dto.response;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Encodes / decodes a keyset cursor: "epochMillis:conversationId"
 *
 * Why keyset (cursor) over OFFSET?
 *  - OFFSET becomes slower the deeper the page (MySQL scans and discards rows)
 *  - Cursor uses the existing idx_inbox index directly — always O(1) seek
 *  - Stable: insertions don't shift rows between pages
 */
public final class CursorUtil {

    private CursorUtil() {}

    public static String encode(Instant lastMessageAt, Long conversationId) {
        String raw = lastMessageAt.toEpochMilli() + ":" + conversationId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static long[] decode(String cursor) {
        // returns [epochMillis, conversationId]
        String raw = new String(
                Base64.getUrlDecoder().decode(cursor),
                StandardCharsets.UTF_8
        );
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid cursor: " + cursor);
        return new long[]{ Long.parseLong(parts[0]), Long.parseLong(parts[1]) };
    }
}