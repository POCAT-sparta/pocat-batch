package com.rocketcrew.pocatbatch.domain.outbox.entity;

import com.rocketcrew.pocatbatch.domain.outbox.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "outbox_events",
        indexes = @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
)
@EntityListeners(AuditingEntityListener.class)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static OutboxEvent pending(String topic, String partitionKey, String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.topic = topic;
        event.partitionKey = partitionKey;
        event.eventType = eventType;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        return event;
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.processedAt = LocalDateTime.now();
    }

    public void markPendingForRetry() {
        this.retryCount++;
        if (this.retryCount >= 5) {
            this.status = OutboxStatus.FAILED;
            this.processedAt = LocalDateTime.now();
        } else {
            this.status = OutboxStatus.PENDING;
        }
    }

    public void changeStatusToProcessing() {
        this.status = OutboxStatus.PROCESSING;
    }

    public void resetToPending() {
        this.status = OutboxStatus.PENDING;
        this.processedAt = null;
    }
}
