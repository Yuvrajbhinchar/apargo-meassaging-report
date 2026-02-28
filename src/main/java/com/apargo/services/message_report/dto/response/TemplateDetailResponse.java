package com.apargo.services.message_report.dto.response;

import com.apargo.services.message_report.entity.WhatsappTemplate;
import com.apargo.services.message_report.entity.WhatsappTemplateCarouselCard;
import com.apargo.services.message_report.entity.WhatsappTemplateCarouselCardComponent;
import com.apargo.services.message_report.entity.WhatsappTemplateComponent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Full template structure returned with every TEMPLATE-type message.
 * Gives the frontend everything it needs to render the template bubble
 * (header image/video, body with substituted variables, footer, buttons,
 * carousel cards).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class TemplateDetailResponse {

    private final Long   templateId;
    private final String name;
    private final String language;
    private final String category;   // MARKETING | UTILITY | AUTHENTICATION
    private final String status;

    /** All components ordered by componentOrder ASC */
    private final List<TemplateComponentResponse> components;

    // ── Factory ───────────────────────────────────────────────────────────

    /**
     * Build a TemplateDetailResponse from a fetched WhatsappTemplate entity,
     * applying variable substitutions from the message's templateVars JSON.
     *
     * @param template     loaded entity (components already in memory)
     * @param templateVars JSON string from messages.template_vars e.g.
     *                     {"body":[["val1","val2"]],"header":["img_url"]}
     */
    public static TemplateDetailResponse from(WhatsappTemplate template, String templateVars) {
        Map<String, Object> vars = parseVars(templateVars);

        List<TemplateComponentResponse> components = template.getComponents()
                .stream()
                .map(c -> mapComponent(c, vars))
                .collect(Collectors.toList());

        return TemplateDetailResponse.builder()
                .templateId(template.getId())
                .name(template.getName())
                .language(template.getLanguage())
                .category(template.getCategory() != null ? template.getCategory().name() : null)
                .status(template.getStatus() != null ? template.getStatus().name() : null)
                .components(components)
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static TemplateComponentResponse mapComponent(
            WhatsappTemplateComponent c,
            Map<String, Object> vars
    ) {
        String componentKey = c.getComponentType() != null
                ? c.getComponentType().name().toLowerCase()
                : null;

        String renderedText = applyVars(c.getText(), componentKey, vars);

        // ── Buttons (non-carousel) ────────────────────────────────────────
        List<TemplateButtonResponse> buttons = c.getButtons()
                .stream()
                .map(b -> TemplateButtonResponse.builder()
                        .buttonType(b.getButtonType() != null ? b.getButtonType().name() : null)
                        .text(b.getText())
                        .url(b.getUrl())
                        .phoneNumber(b.getPhoneNumber())
                        .otpType(b.getOtpType() != null ? b.getOtpType().name() : null)
                        .buttonIndex(b.getButtonIndex())
                        .build())
                .collect(Collectors.toList());

        // ── Carousel cards ────────────────────────────────────────────────
        List<CarouselCardResponse> carouselCards = c.getCarouselCards()
                .stream()
                .map(card -> mapCarouselCard(card, vars))
                .collect(Collectors.toList());

        return TemplateComponentResponse.builder()
                .componentType(componentKey != null ? componentKey.toUpperCase() : null)
                .format(c.getFormat() != null ? c.getFormat().name() : null)
                .text(c.getText())
                .renderedText(renderedText)
                .mediaUrl(c.getMediaUrl())
                .mediaHandle(c.getMediaHandle())
                .addSecurityRecommendation(c.getAddSecurityRecommendation())
                .codeExpirationMinutes(c.getCodeExpirationMinutes())
                .componentOrder(c.getComponentOrder())
                .buttons(buttons.isEmpty() ? null : buttons)
                .carouselCards(carouselCards.isEmpty() ? null : carouselCards)
                .build();
    }

    private static CarouselCardResponse mapCarouselCard(
            WhatsappTemplateCarouselCard card,
            Map<String, Object> vars
    ) {
        String headerFormat   = null;
        String headerMediaUrl = null;
        String headerHandle   = null;
        String bodyText       = null;
        List<TemplateButtonResponse> cardButtons = Collections.emptyList();

        for (WhatsappTemplateCarouselCardComponent sub : card.getCardComponents()) {
            switch (sub.getComponentType() != null ? sub.getComponentType() : "") {
                case "HEADER" -> {
                    headerFormat   = sub.getFormat();
                    headerMediaUrl = sub.getMediaUrl();
                    headerHandle   = sub.getMediaHandle();
                }
                case "BODY" -> bodyText = sub.getText();
                case "BUTTONS" -> cardButtons = sub.getButtons()
                        .stream()
                        .map(b -> TemplateButtonResponse.builder()
                                .buttonType(b.getButtonType())
                                .text(b.getText())
                                .url(b.getUrl())
                                .phoneNumber(b.getPhoneNumber())
                                .buttonIndex(b.getButtonIndex())
                                .build())
                        .collect(Collectors.toList());
            }
        }

        return CarouselCardResponse.builder()
                .cardIndex(card.getCardIndex())
                .headerFormat(headerFormat)
                .headerMediaUrl(headerMediaUrl)
                .headerHandle(headerHandle)
                .bodyText(bodyText)
                .buttons(cardButtons.isEmpty() ? null : cardButtons)
                .build();
    }

    /**
     * Substitute {{1}}, {{2}} … placeholders from the templateVars map.
     * vars structure (Meta convention):
     *   {
     *     "header": ["value"],                          // List<String> for TEXT header
     *     "body":   [["param1", "param2"]],             // List<List<String>> for body
     *     "button": {"0": ["suffix"], "1": ["suffix"]}  // Map<buttonIndex, List<String>>
     *   }
     */
    @SuppressWarnings("unchecked")
    private static String applyVars(String text, String componentKey, Map<String, Object> vars) {
        if (text == null || vars == null || componentKey == null) return null;
        if (!text.contains("{{")) return null; // no variables → skip

        String result = text;

        try {
            Object raw = vars.get(componentKey);

            if ("header".equals(componentKey) && raw instanceof List<?> headerVals) {
                // List<String>
                List<String> values = (List<String>) headerVals;
                for (int i = 0; i < values.size(); i++) {
                    result = result.replace("{{" + (i + 1) + "}}", values.get(i));
                }

            } else if ("body".equals(componentKey) && raw instanceof List<?> outerList) {
                // List<List<String>> — take first row
                if (!outerList.isEmpty() && outerList.get(0) instanceof List<?> inner) {
                    List<String> values = (List<String>) inner;
                    for (int i = 0; i < values.size(); i++) {
                        result = result.replace("{{" + (i + 1) + "}}", values.get(i));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not substitute vars for component '{}': {}", componentKey, e.getMessage());
        }

        return result.equals(text) ? null : result; // null if nothing changed
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseVars(String templateVars) {
        if (templateVars == null || templateVars.isBlank()) return Collections.emptyMap();
        try {
            return new ObjectMapper().readValue(templateVars, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Could not parse templateVars JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}