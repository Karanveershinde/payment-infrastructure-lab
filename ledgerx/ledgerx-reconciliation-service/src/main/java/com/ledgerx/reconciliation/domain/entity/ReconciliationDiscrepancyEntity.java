package com.ledgerx.reconciliation.domain.entity;

import com.ledgerx.common.domain.enums.DiscrepancyResolutionStatus;
import com.ledgerx.common.domain.enums.DiscrepancyType;
import com.ledgerx.common.domain.enums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_discrepancies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationDiscrepancyEntity {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private ReconciliationBatchEntity batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProviderType provider;

    @Column(name = "internal_transaction_id", length = 36)
    private String internalTransactionId;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 40)
    private DiscrepancyType discrepancyType;

    @Column(name = "internal_amount", precision = 18, scale = 4)
    private BigDecimal internalAmount;

    @Column(name = "provider_amount", precision = 18, scale = 4)
    private BigDecimal providerAmount;

    @Column(name = "internal_status", length = 30)
    private String internalStatus;

    @Column(name = "provider_status", length = 30)
    private String providerStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false, length = 30)
    private DiscrepancyResolutionStatus resolutionStatus;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
