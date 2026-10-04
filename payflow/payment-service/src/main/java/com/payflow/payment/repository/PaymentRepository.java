package com.payflow.payment.repository;

import com.payflow.payment.domain.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
    Optional<PaymentEntity> findByIdAndMerchantId(String id, String merchantId);
    Optional<PaymentEntity> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);
    Optional<PaymentEntity> findByProviderTransactionId(String providerTransactionId);
}
