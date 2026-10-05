package com.payflow.payment.repository;

import com.payflow.payment.domain.entity.PaymentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
    Optional<PaymentEntity> findByIdAndMerchantId(String id, String merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.id = :id AND p.merchantId = :merchantId")
    Optional<PaymentEntity> findByIdAndMerchantIdWithLock(@Param("id") String id, @Param("merchantId") String merchantId);

    Optional<PaymentEntity> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);
    Optional<PaymentEntity> findByProviderTransactionId(String providerTransactionId);
}
