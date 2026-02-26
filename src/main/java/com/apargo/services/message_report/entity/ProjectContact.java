package com.apargo.services.message_report.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "project_contacts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_contact",
                        columnNames = {"project_id", "contact_id"}
                )
        }
)
@Getter
@Setter
public class ProjectContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}