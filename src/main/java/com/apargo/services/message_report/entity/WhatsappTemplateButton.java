package com.apargo.services.message_report.entity;

import com.apargo.services.message_report.enums.WaButtonType;
import com.apargo.services.message_report.enums.WaOtpType;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * READ-ONLY mirror of apargo_wa_template.whatsapp_template_buttons.
 * Buttons for non-carousel BUTTONS component.
 */
@Getter
@Immutable
@Entity
@Table(name = "whatsapp_template_buttons", schema = "apargo_wa_template")
public class WhatsappTemplateButton {

    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private WhatsappTemplateComponent component;

    @Enumerated(EnumType.STRING)
    @Column(name = "button_type",
            columnDefinition = "ENUM('URL','QUICK_REPLY','PHONE_NUMBER','COPY_CODE','CATALOG','MPM','SPM','OTP')")
    private WaButtonType buttonType;

    @Column(name = "text", length = 150)
    private String text;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_type",
            columnDefinition = "ENUM('ONE_TAP','COPY_CODE','ZERO_TAP')")
    private WaOtpType otpType;

    @Column(name = "button_index")
    private Integer buttonIndex;

    // JSON array like ["ORDER123"] stored as raw string – frontend resolves
    @Column(name = "example", columnDefinition = "JSON")
    private String example;

    @Column(name = "created_at")
    private Instant createdAt;
}