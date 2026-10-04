package com.payflow.payment.repository;

import com.payflow.payment.domain.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<MerchantEntity, String> {
    Optional<MerchantEntity> findByApiKeyHash(String apiKeyHash);
}
