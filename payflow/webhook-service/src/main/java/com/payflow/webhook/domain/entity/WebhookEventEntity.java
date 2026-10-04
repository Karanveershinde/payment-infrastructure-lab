package com.payflow.webhook.domain.entity;

import com.payflow.common.domain.enums.WebhookEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_provider_event", columnNames = {"provider", "provider_event_id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEventEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "provider", length = 30, nullable = false)
    private String provider;

    @Column(name = "provider_event_id", length = 100, nullable = false)
    private String providerEventId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "signature", length = 255)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private WebhookEventStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;
}
