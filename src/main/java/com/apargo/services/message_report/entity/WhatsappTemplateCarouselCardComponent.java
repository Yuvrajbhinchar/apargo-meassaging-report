package com.apargo.services.message_report.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * READ-ONLY mirror of apargo_wa_template.whatsapp_template_carousel_card_components.
 * Each carousel card has HEADER (image/video), BODY (text), BUTTONS.
 */
@Getter
@Immutable
@Entity
@Table(name = "whatsapp_template_carousel_card_components", schema = "apargo_wa_template")
public class WhatsappTemplateCarouselCardComponent {

    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private WhatsappTemplateCarouselCard card;

    /**
     * HEADER | BODY | BUTTONS
     */
    @Column(name = "component_type",
            columnDefinition = "ENUM('HEADER','BODY','BUTTONS')")
    private String componentType;

    /**
     * IMAGE | VIDEO | DOCUMENT  (only for HEADER)
     */
    @Column(name = "format",
            columnDefinition = "ENUM('IMAGE','VIDEO','DOCUMENT')")
    private String format;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "media_handle", length = 2048)
    private String mediaHandle;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    // ── Buttons (only present when componentType == BUTTONS) ──────────────
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "cardComponent", fetch = FetchType.LAZY)
    @OrderBy("buttonIndex ASC")
    private List<WhatsappTemplateCarouselButton> buttons = new ArrayList<>();
}