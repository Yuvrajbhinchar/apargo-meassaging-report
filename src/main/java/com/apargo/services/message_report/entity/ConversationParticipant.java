package com.apargo.services.message_report.entity;

import java.time.Instant;


import com.apargo.services.message_report.enums.ParticipantRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.ForeignKey;
import lombok.Data;

@Data
@Entity
@Table(name = "conversation_participants")
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_participant_conversation"))
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", columnDefinition = "ENUM('owner','viewer','intervened')")
    private ParticipantRole role = ParticipantRole.VIEWER;

    @Column(name = "added_at")
    private Instant addedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = Instant.now();
    }


}

