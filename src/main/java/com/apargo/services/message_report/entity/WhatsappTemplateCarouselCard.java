package com.apargo.services.message_report.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * READ-ONLY mirror of apargo_wa_template.whatsapp_template_carousel_cards.
 */
@Getter
@Immutable
@Entity
@Table(name = "whatsapp_template_carousel_cards", schema = "apargo_wa_template")
public class WhatsappTemplateCarouselCard {

    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private WhatsappTemplateComponent component;

    @Column(name = "card_index")
    private Integer cardIndex;

    @Column(name = "created_at")
    private Instant createdAt;

    // ── Card sub-components (HEADER / BODY / BUTTONS) ─────────────────────
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "card", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<WhatsappTemplateCarouselCardComponent> cardComponents = new ArrayList<>();
}