package com.ledgerx.ledger.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventEntity.ProcessedEventId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", length = 128)
    private String eventId;

    @Id
    @Column(name = "consumer_name", length = 64)
    private String consumerName;

    @Column(nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedEventId implements Serializable {
        private String eventId;
        private String consumerName;
    }
}
