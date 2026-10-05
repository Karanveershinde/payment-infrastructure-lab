package com.ledgerx.reconciliation.domain.entity;

import com.ledgerx.common.domain.enums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reconciliation_batches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationBatchEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "batch_date", nullable = false)
    private LocalDate batchDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProviderType provider;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "discrepancy_count", nullable = false)
    private int discrepancyCount;

    @Column(name = "total_internal_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalInternalAmount;

    @Column(name = "total_provider_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalProviderAmount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "execution_time_ms", nullable = false)
    private long executionTimeMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
