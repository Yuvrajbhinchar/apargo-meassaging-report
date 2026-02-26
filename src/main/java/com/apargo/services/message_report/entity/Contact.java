package com.apargo.services.message_report.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "contacts"
)
@Getter
@Setter
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "wa_phone_e164", nullable = false, length = 20)
    private String waPhoneE164;

    @Column(name = "wa_id", length = 64)
    private String waId;

    @Column(name = "display_name", length = 150)
    private String displayName;

    // UPDATED: Use custom converter
    @Column(name = "source", nullable = false)
    private Source source = Source.MANUAL;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt = LocalDateTime.now();

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // -------- ENUM (UPDATED: IMPORT instead of import_) --------
    public enum Source {
        MANUAL,      // → "manual" in DB
        IMPORT,      // → "import" in DB (this was the problem!)
        INTEGRATION, // → "integration" in DB
        INBOUND      // → "inbound" in DB
    }
}