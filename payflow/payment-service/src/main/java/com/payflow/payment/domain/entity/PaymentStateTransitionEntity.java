package com.payflow.payment.domain.entity;

import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.StateTransitionTrigger;
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

import java.time.Instant;

@Entity
@Table(name = "payment_state_transitions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStateTransitionEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "payment_id", length = 36, nullable = false)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 30, nullable = false)
    private PaymentStatus fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", length = 30, nullable = false)
    private PaymentStatus toState;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", length = 50, nullable = false)
    private StateTransitionTrigger triggerSource;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
