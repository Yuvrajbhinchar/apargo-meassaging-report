package com.apargo.services.message_report.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * READ-ONLY mirror of the contacts service project_contacts table.
 * Not used by Inbox or Message History APIs currently.
 * Retained for future Contact Detail / Right Panel API.
 *
 * @Immutable prevents accidental INSERT/UPDATE/DELETE from this service.
 */
@Getter
@Immutable
@Entity
@Table(
        name = "project_contacts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_project_contact",
                        columnNames = {"project_id", "contact_id"})
        }
)
public class ProjectContact {

    @Id
    // No @GeneratedValue — this service never inserts project_contacts
    @Column(name = "id")
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "unread_count")
    private Integer unreadCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}