package com.apargo.services.message_report.entity;


import com.apargo.services.message_report.enums.AssignmentType;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;


@Data
@Entity
@Table(name = "conversation_assignments", indexes = {
        @Index(name = "idx_conversation", columnList = "conversation_id"),
        @Index(name = "idx_assigned_to", columnList = "assigned_to")
})
public class ConversationAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_assignment_conversation"))
    private Conversation conversation;

    @Column(name = "assigned_to", nullable = false)
    private Long assignedTo;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, columnDefinition = "ENUM('auto','manual','intervention','transfer')")
    private AssignmentType assignmentType;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    @PrePersist
    protected void onCreate() {
        assignedAt = Instant.now();
    }


}

