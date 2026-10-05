package com.ledgerx.ledger.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "dead_letter_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterEventEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "partition_num")
    private Integer partitionNum;

    @Column(name = "offset_num")
    private Long offsetNum;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(columnDefinition = "TEXT")
    private String stacktrace;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "first_failed_at", nullable = false, updatable = false)
    private Instant firstFailedAt;

    @UpdateTimestamp
    @Column(name = "last_failed_at", nullable = false)
    private Instant lastFailedAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;
}
