package com.apargo.services.message_report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Represents one component of a WhatsApp template
 * (HEADER / BODY / FOOTER / BUTTONS / CAROUSEL / LIMITED_TIME_OFFER).
 *
 * The UI uses componentType to decide how to render:
 *  - HEADER + format=TEXT   → render text
 *  - HEADER + format=IMAGE  → render image from mediaUrl
 *  - BODY                   → render text (with variable substitutions in renderedText)
 *  - FOOTER                 → render small grey text
 *  - BUTTONS                → render button list
 *  - CAROUSEL               → render horizontal card list
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateComponentResponse {

    private final String componentType;   // HEADER | BODY | FOOTER | BUTTONS | CAROUSEL | LIMITED_TIME_OFFER
    private final String format;          // TEXT | IMAGE | VIDEO | DOCUMENT | LOCATION | PRODUCT

    // ── Text content ──────────────────────────────────────────────────────
    /** Original template text with {{1}}, {{2}} placeholders */
    private final String text;

    /**
     * Rendered text with variables substituted from templateVars.
     * NULL if no variables were used or type is not text-based.
     */
    private final String renderedText;

    // ── Media (HEADER with IMAGE / VIDEO / DOCUMENT) ─────────────────────
    private final String mediaUrl;        // CDN/upload URL stored on template
    private final String mediaHandle;     // Meta resumable upload handle

    // ── Buttons (BUTTONS component) ───────────────────────────────────────
    private final List<TemplateButtonResponse> buttons;

    // ── Carousel cards (CAROUSEL component) ──────────────────────────────
    private final List<CarouselCardResponse> carouselCards;

    // ── Auth-specific flags ───────────────────────────────────────────────
    private final Boolean addSecurityRecommendation;
    private final Integer codeExpirationMinutes;

    private final Integer componentOrder;
}