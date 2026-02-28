package com.apargo.services.message_report.entity;

import java.time.Instant;

import com.apargo.services.message_report.enums.ParticipantRole;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

@Data
@Entity
@Table(name = "conversation_participants")
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_participant_conversation"))
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", columnDefinition = "ENUM('OWNER','VIEWER','INTERVENED')")
    private ParticipantRole role = ParticipantRole.VIEWER;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false)
    private Instant addedAt;

    @Column(name = "removed_at")
    private Instant removedAt;
}