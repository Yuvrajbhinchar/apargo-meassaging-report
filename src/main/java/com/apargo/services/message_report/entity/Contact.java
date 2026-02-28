package com.apargo.services.message_report.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * READ-ONLY mirror of the contacts service table.
 *
 * @Immutable  → Hibernate NEVER issues INSERT / UPDATE / DELETE for this entity.
 *               Any accidental save() call throws immediately.
 *               Also skips dirty-checking → faster flush cycle.
 *
 * No @GeneratedValue → this service never inserts contacts.
 * No @CreationTimestamp / @UpdateTimestamp → those only make sense on owned entities.
 */
@Getter
@Immutable
@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    // NO @GeneratedValue — we are READING existing contacts, never inserting
    @Column(name = "id")
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "wa_phone_e164", length = 20)
    private String waPhoneE164;

    @Column(name = "wa_id", length = 64)
    private String waId;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Enumerated(EnumType.STRING)          // explicit — never rely on default
    @Column(name = "source")
    private Source source;

    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Source {
        MANUAL,
        IMPORT,
        INTEGRATION,
        INBOUND
    }
}