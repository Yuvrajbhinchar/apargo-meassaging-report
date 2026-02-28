package com.apargo.services.message_report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CarouselCardResponse {

    private final Integer               cardIndex;

    // HEADER sub-component
    private final String                headerFormat;   // IMAGE | VIDEO | DOCUMENT
    private final String                headerMediaUrl; // resolved CDN/upload URL
    private final String                headerHandle;   // Meta upload handle (alternate)

    // BODY sub-component
    private final String                bodyText;

    // BUTTONS sub-component
    private final List<TemplateButtonResponse> buttons;
}