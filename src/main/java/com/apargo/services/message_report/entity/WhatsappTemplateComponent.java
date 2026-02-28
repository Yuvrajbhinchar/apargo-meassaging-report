package com.apargo.services.message_report.entity;

import com.apargo.services.message_report.enums.WaComponentFormat;
import com.apargo.services.message_report.enums.WaComponentType;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * READ-ONLY mirror of apargo_wa_template.whatsapp_template_components.
 * Represents HEADER / BODY / FOOTER / BUTTONS / CAROUSEL / LTO.
 */
@Getter
@Immutable
@Entity
@Table(name = "whatsapp_template_components", schema = "apargo_wa_template")
public class WhatsappTemplateComponent {

    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private WhatsappTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type",
            columnDefinition = "ENUM('HEADER','BODY','FOOTER','BUTTONS','CAROUSEL','LIMITED_TIME_OFFER')")
    private WaComponentType componentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format",
            columnDefinition = "ENUM('TEXT','IMAGE','VIDEO','DOCUMENT','LOCATION','PRODUCT')")
    private WaComponentFormat format;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "media_handle", length = 2048)
    private String mediaHandle;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "add_security_recommendation")
    private Boolean addSecurityRecommendation;

    @Column(name = "code_expiration_minutes")
    private Integer codeExpirationMinutes;

    @Column(name = "component_order")
    private Integer componentOrder;

    @Column(name = "created_at")
    private Instant createdAt;

    // ── Relationships ─────────────────────────────────────────────────────

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "component", fetch = FetchType.LAZY)
    @OrderBy("buttonIndex ASC")
    private List<WhatsappTemplateButton> buttons = new ArrayList<>();

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "component", fetch = FetchType.LAZY)
    @OrderBy("cardIndex ASC")
    private List<WhatsappTemplateCarouselCard> carouselCards = new ArrayList<>();
}