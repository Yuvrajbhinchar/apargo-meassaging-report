package com.apargo.services.message_report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.ForeignKey;

@Data
@Entity
@Table(name = "message_status_rollup")
public class MessageStatusRollup {

    @Id
    @Column(name = "message_id")
    private Long messageId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "message_id", foreignKey = @ForeignKey(name = "fk_rollup_message"))
    private Message message;

    @Column(name = "is_sent", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isSent = false;

    @Column(name = "is_delivered", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isDelivered = false;

    @Column(name = "is_read", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isRead = false;

    @Column(name = "is_failed", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isFailed = false;

    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = Instant.now();
    }
}
