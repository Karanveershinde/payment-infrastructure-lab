package com.payflow.payment.repository;

import com.payflow.payment.domain.entity.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyEntity.IdempotencyKeyId> {
    Optional<IdempotencyKeyEntity> findById_MerchantIdAndId_IdempotencyKey(String merchantId, String idempotencyKey);
    void deleteByExpiresAtBefore(Instant now);
}
