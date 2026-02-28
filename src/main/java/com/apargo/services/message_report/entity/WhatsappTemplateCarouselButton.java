package com.apargo.services.message_report.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * READ-ONLY mirror of apargo_wa_template.whatsapp_template_carousel_buttons.
 */
@Getter
@Immutable
@Entity
@Table(name = "whatsapp_template_carousel_buttons", schema = "apargo_wa_template")
public class WhatsappTemplateCarouselButton {

    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_component_id")
    private WhatsappTemplateCarouselCardComponent cardComponent;

    @Column(name = "button_type",
            columnDefinition = "ENUM('URL','QUICK_REPLY','PHONE_NUMBER')")
    private String buttonType;

    @Column(name = "text", length = 150)
    private String text;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "button_index")
    private Integer buttonIndex;

    @Column(name = "created_at")
    private Instant createdAt;
}