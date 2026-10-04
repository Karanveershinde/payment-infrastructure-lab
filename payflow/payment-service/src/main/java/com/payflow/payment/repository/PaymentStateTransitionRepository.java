package com.payflow.payment.repository;

import com.payflow.payment.domain.entity.PaymentStateTransitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentStateTransitionRepository extends JpaRepository<PaymentStateTransitionEntity, String> {
    List<PaymentStateTransitionEntity> findByPaymentIdOrderByCreatedAtAsc(String paymentId);
}
