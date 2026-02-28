package com.apargo.services.message_report.entity;

import com.apargo.services.message_report.enums.WaTemplateCategory;
import com.apargo.services.message_report.enums.WaTemplateStatus;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * READ-ONLY mirror of apargo_wa_template.whatsapp_templates.
 * Used to enrich TEMPLATE-type messages with their component structure.
 *
 * @Immutable prevents any accidental INSERT / UPDATE / DELETE.
 * Cross-schema access: schema = "apargo_wa_template" tells Hibernate
 * to generate fully-qualified SQL: apargo_wa_template.whatsapp_templates
 */
@Getter
@Immutable
@Entity
@Table(name = "whatsapp_templates", schema = "apargo_wa_template")
public class WhatsappTemplate {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "waba_account_id")
    private String wabaAccountId;

    @Column(name = "name", length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category",
            columnDefinition = "ENUM('MARKETING','UTILITY','AUTHENTICATION')")
    private WaTemplateCategory category;

    @Column(name = "language", length = 10)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",
            columnDefinition = "ENUM('DRAFT','NEW_CREATED','SUBMITTED','PENDING','APPROVED','REJECTED','PAUSED','DISABLED','FAILED')")
    private WaTemplateStatus status;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "meta_template_id", length = 150)
    private String metaTemplateId;

    @Column(name = "quality_rating",
            columnDefinition = "ENUM('GREEN','YELLOW','RED','UNKNOWN')")
    private String qualityRating;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ── Relationships (LAZY – only loaded when needed) ────────────────────

    @BatchSize(size = 30)
    @OneToMany(mappedBy = "template", fetch = FetchType.LAZY)
    @OrderBy("componentOrder ASC")
    private List<WhatsappTemplateComponent> components = new ArrayList<>();
}