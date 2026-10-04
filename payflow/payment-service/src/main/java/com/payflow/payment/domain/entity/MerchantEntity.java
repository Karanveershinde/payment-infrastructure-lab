package com.payflow.payment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "merchants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "api_key_hash", length = 64, nullable = false, unique = true)
    private String apiKeyHash;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "webhook_url", length = 255)
    private String webhookUrl;

    @Column(name = "rate_limit_per_minute", nullable = false)
    @Builder.Default
    private int rateLimitPerMinute = 100;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
